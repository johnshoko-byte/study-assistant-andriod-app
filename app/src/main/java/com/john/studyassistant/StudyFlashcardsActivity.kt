package com.john.studyassistant

import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class StudyFlashcardsActivity : AppCompatActivity() {

    private lateinit var deckTitleView: TextView
    private lateinit var flashcardView: CardView
    private lateinit var cardContainer: FrameLayout
    private lateinit var resultLayout: LinearLayout
    private lateinit var resultSummary: TextView
    private lateinit var tryAgainButton: Button
    private lateinit var homeButton: Button

    private lateinit var db: DatabaseReference
    private val auth = FirebaseAuth.getInstance()

    private var deckId: String = ""
    private val flashcards = mutableListOf<FlashcardCard>()
    private var currentIndex = 0
    private var showingTerm = true
    private var flashcardText: TextView? = null

    private var knownCount = 0
    private var unknownCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_flashcards)

        // Views
        deckTitleView = findViewById(R.id.deckTitleView)
        flashcardView = findViewById(R.id.flashcardView)
        cardContainer = findViewById(R.id.cardContainer)
        resultLayout = findViewById(R.id.resultLayout)
        resultSummary = findViewById(R.id.resultSummary)
        tryAgainButton = findViewById(R.id.tryAgainButton)
        homeButton = findViewById(R.id.homeButton)

        // Deck ID from intent
        deckId = intent.getStringExtra("deckId") ?: ""

        db = FirebaseDatabase.getInstance(
            "https://study20070831-default-rtdb.europe-west1.firebasedatabase.app/"
        ).reference

        // Camera distance for 3D flip
        val distance = 8000
        flashcardView.cameraDistance = resources.displayMetrics.density * distance

        // Load deck title and cards
        loadDeckTitle()
        loadDeckCards()
    }

    /** Fetch deck title from Firebase */
    private fun loadDeckTitle() {
        if (deckId.isEmpty()) return

        db.child("decks").child(deckId).child("title")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val title = snapshot.getValue(String::class.java)
                    if (!title.isNullOrEmpty()) {
                        deckTitleView.text = title
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@StudyFlashcardsActivity, "Failed to load deck title", Toast.LENGTH_SHORT).show()
                }
            })
    }

    /** Load flashcards for the deck */
    private fun loadDeckCards() {
        if (deckId.isEmpty()) return

        db.child("decks").child(deckId).child("cards")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    flashcards.clear()
                    for (snap in snapshot.children) {
                        val card = snap.getValue(FlashcardCard::class.java)
                        if (card != null) flashcards.add(card)
                    }

                    if (flashcards.isEmpty()) {
                        val tv = TextView(this@StudyFlashcardsActivity).apply {
                            text = "No cards in this deck."
                            textSize = 18f
                            setPadding(24, 24, 24, 24)
                        }
                        cardContainer.addView(tv)
                        return
                    }

                    showFlashcard()
                    setupGestures()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@StudyFlashcardsActivity, "Failed to load cards", Toast.LENGTH_SHORT).show()
                }
            })
    }

    /** Display the current flashcard */
    private fun showFlashcard() {
        if (currentIndex >= flashcards.size) {
            showSummary()
            return
        }

        val card = flashcards[currentIndex]
        showingTerm = true

        cardContainer.removeAllViews()
        flashcardText = TextView(this).apply {
            text = card.term
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        cardContainer.addView(flashcardText)

        // Reset rotation & background
        flashcardView.rotationY = 0f
        flashcardText?.rotationY = 0f
        flashcardView.setCardBackgroundColor(Color.WHITE)
        resultLayout.visibility = View.GONE
    }

    /** Flip card with smooth 3D animation */
    private fun flipCard() {
        if (currentIndex >= flashcards.size) return
        val card = flashcards[currentIndex]

        val distance = 8000
        flashcardView.cameraDistance = resources.displayMetrics.density * distance
        flashcardText?.cameraDistance = resources.displayMetrics.density * distance

        flashcardView.animate()
            .rotationY(90f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                showingTerm = !showingTerm
                flashcardText?.text = if (showingTerm) card.term else card.definition

                flashcardView.rotationY = -90f
                flashcardView.animate()
                    .rotationY(0f)
                    .setDuration(180)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }.start()
    }

    /** Gesture setup for tap & swipe */
    private fun setupGestures() {
        val SWIPE_THRESHOLD = 100
        val SWIPE_VELOCITY_THRESHOLD = 100

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                    if (kotlin.math.abs(diffX) > SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) markKnown() else markUnknown()
                        return true
                    }
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                flipCard()
                return true
            }
        })

        flashcardView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun markKnown() {
        flashcardView.setCardBackgroundColor(Color.parseColor("#4CAF50"))
        knownCount++
        nextCard()
    }

    private fun markUnknown() {
        flashcardView.setCardBackgroundColor(Color.parseColor("#F44336"))
        unknownCount++
        nextCard()
    }

    private fun nextCard() {
        currentIndex++
        if (currentIndex >= flashcards.size) {
            flashcardView.postDelayed({ showSummary() }, 400)
        } else {
            flashcardView.postDelayed({ showFlashcard() }, 300)
        }
    }

    /** Show summary and update recent decks */
    private fun showSummary() {
        flashcardView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                flashcardView.alpha = 1f
                flashcardView.visibility = View.GONE
                cardContainer.removeAllViews()

                resultLayout.alpha = 0f
                resultLayout.visibility = View.VISIBLE
                resultLayout.animate().alpha(1f).setDuration(300).start()

                resultSummary.text = """
                Study session complete!
                ✅ Known: $knownCount
                ❌ Unknown: $unknownCount
            """.trimIndent()

                updateRecentDecks()

                tryAgainButton.setOnClickListener {
                    currentIndex = 0
                    knownCount = 0
                    unknownCount = 0
                    resultLayout.visibility = View.GONE
                    flashcardView.visibility = View.VISIBLE // <-- show card again
                    showFlashcard()
                }

                homeButton.setOnClickListener { finish() }
            }.start()
    }


    /** Save deck to recent decks and keep top 2 */
    private fun updateRecentDecks() {
        val uid = auth.currentUser?.uid ?: return
        val recentRef = db.child("users").child(uid).child("recentDecks")
        val timestamp = System.currentTimeMillis()

        // Save or update current deck timestamp
        recentRef.child(deckId).setValue(timestamp).addOnSuccessListener {
            // Keep only top 2 recent decks
            recentRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sortedDecks = snapshot.children
                        .sortedByDescending { it.getValue(Long::class.java) ?: 0L }
                        .take(2)
                        .mapNotNull { it.key }
                        .toSet()

                    for (child in snapshot.children) {
                        val key = child.key ?: continue
                        if (key !in sortedDecks) {
                            child.ref.removeValue()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

}
