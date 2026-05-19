package com.john.studyassistant

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AddDeckActivity : AppCompatActivity() {

    private lateinit var edtDeckTitle: EditText
    private lateinit var btnSaveDeck: Button
    private lateinit var btnAddCard: Button
    private lateinit var radioPublic: RadioButton
    private lateinit var radioPrivate: RadioButton
    private lateinit var cardsContainer: LinearLayout

    private lateinit var auth: FirebaseAuth
    private val dbRef = FirebaseDatabase.getInstance(
        "https://study20070831-default-rtdb.europe-west1.firebasedatabase.app/"
    ).reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_deck)

        // Initialize views
        edtDeckTitle = findViewById(R.id.etDeckTitle)
        btnSaveDeck = findViewById(R.id.btnSaveDeck)
        btnAddCard = findViewById(R.id.btnAddCard)
        radioPublic = findViewById(R.id.radioPublic)
        radioPrivate = findViewById(R.id.radioPrivate)
        cardsContainer = findViewById(R.id.cardsContainer)

        auth = FirebaseAuth.getInstance()

        btnAddCard.setOnClickListener { addCardRow() }
        btnSaveDeck.setOnClickListener { saveDeck() }
    }

    /** Add a new card row dynamically */
    private fun addCardRow() {
        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val edtTerm = EditText(this).apply {
            hint = "Term"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
        }

        val edtDefinition = EditText(this).apply {
            hint = "Definition"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
        }

        rowLayout.addView(edtTerm)
        rowLayout.addView(edtDefinition)
        cardsContainer.addView(rowLayout)
    }

    /** Save deck + cards to Firebase */
    private fun saveDeck() {
        val title = edtDeckTitle.text.toString().trim()
        val userId = auth.currentUser?.uid

        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a deck title", Toast.LENGTH_SHORT).show()
            return
        }

        if (userId == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show()
            Log.e("AddDeckActivity", "User ID is null — not logged in.")
            return
        }

        val isPublic = radioPublic.isChecked
        val deckId = dbRef.child("users").child(userId).child("decks").push().key

        if (deckId == null) {
            Toast.makeText(this, "Failed to generate deck ID", Toast.LENGTH_SHORT).show()
            Log.e("AddDeckActivity", "Deck ID generation failed.")
            return
        }

        val deckData = mapOf(
            "id" to deckId,
            "title" to title,
            "ownerId" to userId,
            "cardCount" to cardsContainer.childCount,
            "isPublic" to isPublic
        )

        // Save deck under user
        dbRef.child("users").child(userId).child("decks").child(deckId)
            .setValue(deckData)
            .addOnSuccessListener {
                Log.d("AddDeckActivity", "Deck saved under user.")
                saveCards(userId, deckId, isPublic)
                Toast.makeText(this, "Deck saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("AddDeckActivity", "Failed to save deck: ${e.message}")
                Toast.makeText(this, "Error saving deck", Toast.LENGTH_SHORT).show()
            }

        // Save public deck if applicable
        if (isPublic) {
            dbRef.child("decks").child(deckId)
                .setValue(deckData)
                .addOnSuccessListener { Log.d("AddDeckActivity", "Deck shared publicly.") }
                .addOnFailureListener { e -> Log.e("AddDeckActivity", "Failed to share deck: ${e.message}") }
        }
    }

    /** Save all cards under user deck and public deck if needed */
    private fun saveCards(userId: String, deckId: String, isPublic: Boolean) {
        for (i in 0 until cardsContainer.childCount) {
            val row = cardsContainer.getChildAt(i) as LinearLayout
            val term = (row.getChildAt(0) as EditText).text.toString().trim()
            val definition = (row.getChildAt(1) as EditText).text.toString().trim()

            if (term.isEmpty() || definition.isEmpty()) continue

            val cardId = dbRef.child("users").child(userId)
                .child("decks").child(deckId).child("cards").push().key ?: continue

            val cardData = mapOf(
                "term" to term,
                "definition" to definition
            )

            // Save under user's deck
            dbRef.child("users").child(userId).child("decks")
                .child(deckId).child("cards").child(cardId)
                .setValue(cardData)
                .addOnSuccessListener { Log.d("AddDeckActivity", "Saved card: $term") }
                .addOnFailureListener { e -> Log.e("AddDeckActivity", "Failed to save card: ${e.message}") }

            // Save under public deck if applicable
            if (isPublic) {
                dbRef.child("decks").child(deckId).child("cards").child(cardId)
                    .setValue(cardData)
                    .addOnFailureListener { e -> Log.e("AddDeckActivity", "Failed to save public card: ${e.message}") }
            }
        }
    }
}
