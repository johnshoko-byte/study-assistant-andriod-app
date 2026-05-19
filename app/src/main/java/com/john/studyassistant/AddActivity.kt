package com.john.studyassistant

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

class AddActivity : AppCompatActivity() {

    private lateinit var btnAddDeck: MaterialCardView
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)

        btnAddDeck = findViewById(R.id.btnAddDeck)
        bottomNav = findViewById(R.id.bottomNav)

        // Card to create new deck
        btnAddDeck.setOnClickListener {
            val intent = Intent(this, AddDeckActivity::class.java)
            startActivity(intent)
        }

        // Bottom Navigation
        bottomNav.selectedItemId = R.id.add
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.add -> true
                R.id.folder -> {
                    startActivity(Intent(this, LibraryActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
