package com.john.studyassistant

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DeckDetailActivity : AppCompatActivity() {

    private lateinit var deckTitleView: TextView
    private lateinit var studyDeckButton: Button
    private lateinit var deleteDeckButton: Button
    private lateinit var cardsContainer: LinearLayout

    private lateinit var db: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private var deckId: String = ""
    private var deckTitle: String = ""
    private var ownerId: String = ""
    private var isPublic: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deck_detail)

        deckTitleView = findViewById(R.id.deckTitleView)
        studyDeckButton = findViewById(R.id.studyDeckButton)
        deleteDeckButton = findViewById(R.id.deleteDeckButton)
        cardsContainer = findViewById(R.id.cardsContainer)

        db = FirebaseDatabase.getInstance(
            "https://study20070831-default-rtdb.europe-west1.firebasedatabase.app/"
        ).reference
        auth = FirebaseAuth.getInstance()

        deckId = intent.getStringExtra("deckId") ?: ""
        deckTitle = intent.getStringExtra("deckTitle") ?: "Untitled Deck"

        deckTitleView.text = deckTitle

        // Load deck details and cards
        loadDeckDetails()

        studyDeckButton.setOnClickListener {
            val intent = Intent(this, StudyFlashcardsActivity::class.java)
            intent.putExtra("deckId", deckId)
            intent.putExtra("deckTitle", deckTitle)
            startActivity(intent)
        }


        deleteDeckButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Deck")
                .setMessage("Are you sure you want to delete '$deckTitle'?")
                .setPositiveButton("Delete") { _, _ -> deleteDeck() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /** Load deck info (ownerId + isPublic) then its cards */
    private fun loadDeckDetails() {
        if (deckId.isEmpty()) return

        // First try public /decks path
        db.child("decks").child(deckId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val deck = snapshot.getValue(FlashcardDeck::class.java)
                        if (deck != null) {
                            ownerId = deck.ownerId ?: ""
                            isPublic = deck.isPublic
                            deckTitle = deck.title ?: "Untitled Deck"
                            deckTitleView.text = deckTitle
                            loadDeckCards(isUserDeck = false)
                        }
                    } else {
                        // If not public, check user's private decks
                        val uid = auth.currentUser?.uid ?: return
                        db.child("users").child(uid).child("decks").child(deckId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnap: DataSnapshot) {
                                    if (userSnap.exists()) {
                                        val deck = userSnap.getValue(FlashcardDeck::class.java)
                                        if (deck != null) {
                                            ownerId = deck.ownerId ?: uid
                                            isPublic = deck.isPublic
                                            deckTitle = deck.title ?: "Untitled Deck"
                                            deckTitleView.text = deckTitle
                                            loadDeckCards(isUserDeck = true)
                                        }
                                    } else {
                                        Toast.makeText(
                                            this@DeckDetailActivity,
                                            "Deck not found.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        finish()
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(
                                        this@DeckDetailActivity,
                                        "Error loading deck.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@DeckDetailActivity, "Error loading deck.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    /** Load cards based on deck type (user/public) */
    private fun loadDeckCards(isUserDeck: Boolean) {
        val cardsRef = if (isUserDeck) {
            val uid = auth.currentUser?.uid ?: return
            db.child("users").child(uid).child("decks").child(deckId).child("cards")
        } else {
            db.child("decks").child(deckId).child("cards")
        }

        cardsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cardsContainer.removeAllViews()

                if (!snapshot.exists()) {
                    val placeholder = TextView(this@DeckDetailActivity).apply {
                        text = "No cards added yet."
                        textSize = 16f
                        setPadding(16, 16, 16, 16)
                    }
                    cardsContainer.addView(placeholder)
                    return
                }

                for (cardSnap in snapshot.children) {
                    val term = cardSnap.child("term").getValue(String::class.java) ?: ""
                    val definition = cardSnap.child("definition").getValue(String::class.java) ?: ""
                    addCardView(term, definition)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@DeckDetailActivity, "Error loading cards", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /** Display a flashcard visually */
    private fun addCardView(term: String, definition: String) {
        val card = MaterialCardView(this).apply {
            radius = 16f
            cardElevation = 6f
            useCompatPadding = true
            setContentPadding(20, 16, 20, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }

        val tv = TextView(this).apply {
            text = "Term: $term\nDefinition: $definition"
            textSize = 16f
        }

        card.addView(tv)
        cardsContainer.addView(card)
    }

    /** Delete deck (only if owned by current user) */
    private fun deleteDeck() {
        val uid = auth.currentUser?.uid ?: return

        if (ownerId != uid) {
            Toast.makeText(this, "You can only delete your own decks.", Toast.LENGTH_SHORT).show()
            return
        }

        // Remove from both locations just in case
        val userDeckRef = db.child("users").child(uid).child("decks").child(deckId)
        val globalDeckRef = db.child("decks").child(deckId)

        userDeckRef.removeValue()
        globalDeckRef.removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Deck deleted successfully.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete deck.", Toast.LENGTH_SHORT).show()
            }
    }
}
