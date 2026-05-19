package com.john.studyassistant

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class LibraryActivity : AppCompatActivity() {

    private lateinit var decksContainer: LinearLayout
    private lateinit var suggestedContainer: LinearLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var searchInput: EditText

    private val auth = FirebaseAuth.getInstance()
    private val dbRoot = FirebaseDatabase.getInstance(
        "https://study20070831-default-rtdb.europe-west1.firebasedatabase.app/"
    ).reference

    private val userDecks = mutableListOf<FlashcardDeck>()
    private val publicDecks = mutableListOf<FlashcardDeck>()
    private val allDecks = mutableListOf<FlashcardDeck>()
    private val userNamesCache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        decksContainer = findViewById(R.id.decksContainer)
        suggestedContainer = findViewById(R.id.suggestedContainer)
        bottomNav = findViewById(R.id.bottomNav)
        searchInput = findViewById(R.id.librarySearchInput)

        loadDecks()

        searchInput.addTextChangedListener { editable ->
            filterDecks(editable.toString())
        }

        bottomNav.selectedItemId = R.id.folder
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> navigateTo(HomeActivity::class.java)
                R.id.add -> navigateTo(AddActivity::class.java)
                R.id.folder -> true
                R.id.profile -> navigateTo(ProfileActivity::class.java)
                else -> false
            }
        }
    }

    private fun navigateTo(activityClass: Class<*>): Boolean {
        startActivity(Intent(this, activityClass))
        overridePendingTransition(0, 0)
        finish()
        return true
    }

    /** Load user decks and public decks */
    private fun loadDecks() {
        val userId = auth.currentUser?.uid ?: return

        // 🔹 Load User Decks
        dbRoot.child("users").child(userId).child("decks")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userDecks.clear()
                    for (deckSnap in snapshot.children) {
                        val deck = deckSnap.getValue(FlashcardDeck::class.java)
                        if (deck != null) {
                            deck.id = deckSnap.key ?: ""
                            userDecks.add(deck)
                        }
                    }
                    displayDecks(userDecks, decksContainer, showCreator = false)
                    updateAllDecksList()
                }

                override fun onCancelled(error: DatabaseError) {
                    showError("Failed to load user decks: ${error.message}")
                }
            })

        // 🔹 Load Public Decks
        dbRoot.child("decks")
            .orderByChild("isPublic").equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userId = auth.currentUser?.uid ?: ""
                    publicDecks.clear()
                    for (deckSnap in snapshot.children) {
                        val deck = deckSnap.getValue(FlashcardDeck::class.java)
                        if (deck != null) {
                            deck.id = deckSnap.key ?: ""
                            if (deck.ownerId != userId) publicDecks.add(deck)
                        }
                    }
                    displayDecks(publicDecks.shuffled(), suggestedContainer, showCreator = true)
                    updateAllDecksList()
                }

                override fun onCancelled(error: DatabaseError) {
                    showError("Failed to load public decks: ${error.message}")
                }
            })
    }

    /** Keep a combined list for search */
    private fun updateAllDecksList() {
        allDecks.clear()
        allDecks.addAll(userDecks)
        allDecks.addAll(publicDecks)
    }

    /** Filter decks by search query */
    private fun filterDecks(query: String) {
        if (query.isEmpty()) {
            displayDecks(userDecks, decksContainer, showCreator = false)
            displayDecks(publicDecks.shuffled(), suggestedContainer, showCreator = true)
            return
        }

        val filtered = allDecks.filter {
            it.title?.contains(query, ignoreCase = true) == true
        }

        decksContainer.removeAllViews()
        suggestedContainer.removeAllViews()

        if (filtered.isEmpty()) {
            val noResult = TextView(this).apply {
                text = "No decks found."
                textSize = 16f
                setPadding(24, 24, 24, 24)
            }
            decksContainer.addView(noResult)
        } else {
            val filteredUser = filtered.filter { it.ownerId == auth.currentUser?.uid }
            val filteredPublic = filtered.filter { it.ownerId != auth.currentUser?.uid }

            displayDecks(filteredUser, decksContainer, showCreator = false)
            displayDecks(filteredPublic, suggestedContainer, showCreator = true)
        }
    }

    /** Display deck cards with author next to title */
    private fun displayDecks(decks: List<FlashcardDeck>, container: LinearLayout, showCreator: Boolean) {
        container.removeAllViews()

        for (deck in decks) {
            val deckCard = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(16, 8, 16, 8) }
                radius = 16f
                cardElevation = 4f
                setPadding(24, 24, 24, 24)
                isClickable = true
                isFocusable = true
            }

            // Horizontal layout for title + author
            val horizontalLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val titleView = MaterialTextView(this).apply {
                text = deck.title ?: "Untitled Deck"
                textSize = 16f
                setPadding(0, 0, 8, 0)
            }

            horizontalLayout.addView(titleView)

            if (showCreator) {
                val creatorView = MaterialTextView(this).apply {
                    textSize = 14f
                    setPadding(8, 0, 0, 0)
                }

                val ownerIdCopy = deck.ownerId ?: ""
                if (ownerIdCopy.isNotEmpty()) {
                    val cachedName = userNamesCache[ownerIdCopy]
                    if (cachedName != null) {
                        creatorView.text = "by $cachedName"
                    } else {
                        dbRoot.child("users").child(ownerIdCopy).child("username")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    val name = snapshot.getValue(String::class.java) ?: "Unknown"
                                    userNamesCache[ownerIdCopy] = name
                                    creatorView.text = "by $name"
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    creatorView.text = "by Unknown"
                                }
                            })
                    }
                } else {
                    creatorView.text = "by Unknown"
                }

                horizontalLayout.addView(creatorView)
            }

            deckCard.addView(horizontalLayout)

            deckCard.setOnClickListener {
                val intent = Intent(this, DeckDetailActivity::class.java)
                intent.putExtra("deckId", deck.id)
                startActivity(intent)
            }

            container.addView(deckCard)
        }
    }

    private fun showError(msg: String) {
        val errorMsg = TextView(this).apply {
            text = msg
            textSize = 16f
            setPadding(24, 24, 24, 24)
        }
        decksContainer.addView(errorMsg)
    }
}
