package com.john.studyassistant

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.squareup.picasso.Picasso

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var usernameText: TextView
    private lateinit var friendsContainer: LinearLayout
    private lateinit var friendRequestsContainer: LinearLayout
    private lateinit var searchResultsContainer: LinearLayout
    private lateinit var searchFriendsInput: EditText
    private lateinit var noResultsText: TextView // ✅ added
    private lateinit var optionsButton: ImageButton
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var xpBar: ProgressBar
    private lateinit var levelText: TextView
    private lateinit var xpText: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance(
            "https://study20070831-default-rtdb.europe-west1.firebasedatabase.app/"
        ).reference

        profileImage = findViewById(R.id.profileImage)
        usernameText = findViewById(R.id.usernameText)
        levelText = findViewById(R.id.levelText)
        xpBar = findViewById(R.id.xpBar)
        xpText = findViewById(R.id.xpText)
        friendsContainer = findViewById(R.id.friendsContainer)
        friendRequestsContainer = findViewById(R.id.friendRequestsContainer)
        searchResultsContainer = findViewById(R.id.resultsContainer)
        searchFriendsInput = findViewById(R.id.searchFriendsInput)
        optionsButton = findViewById(R.id.optionsButton)
        bottomNav = findViewById(R.id.bottomNav)

        // ✅ Dynamically create "no results" label if not in XML
        noResultsText = TextView(this).apply {
            text = "No users found"
            textSize = 16f
            visibility = View.GONE
            setPadding(16, 16, 16, 16)
        }
        searchResultsContainer.addView(noResultsText)

        bottomNav.selectedItemId = R.id.profile
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> { navigateTo(HomeActivity::class.java); true }
                R.id.add -> { navigateTo(AddActivity::class.java); true }
                R.id.folder -> { navigateTo(LibraryActivity::class.java); true }
                R.id.profile -> true
                else -> false
            }
        }

        optionsButton.setOnClickListener { showOptionsMenu() }

        loadProfileData()
        loadFriends()
        loadFriendRequests()
        setupFriendSearch()
        observeUserXP()
    }

    private fun navigateTo(cls: Class<*>) {
        startActivity(Intent(this, cls))
        overridePendingTransition(0, 0)
        finish()
    }

    private fun showOptionsMenu() {
        val popup = android.widget.PopupMenu(this, optionsButton)
        popup.menuInflater.inflate(R.menu.profile_options_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_change_username -> { showChangeUsernameDialog(); true }
                R.id.menu_change_picture -> { showChangeProfilePicDialog(); true }
                R.id.menu_logout -> { auth.signOut(); finish(); true }
                R.id.menu_delete_account -> {
                    auth.currentUser?.delete()
                    db.child("users").child(auth.uid!!).removeValue()
                    Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
                    finish()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showChangeUsernameDialog() {
        val input = EditText(this)
        input.hint = "Enter new username"
        AlertDialog.Builder(this)
            .setTitle("Change Username")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newNameLower = newName.lowercase()
                    db.child("users").child(auth.uid!!).apply {
                        child("username").setValue(newName)
                        child("usernameLower").setValue(newNameLower)
                    }
                    Toast.makeText(this, "Username updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChangeProfilePicDialog() {
        val input = EditText(this)
        input.hint = "Paste image URL"
        AlertDialog.Builder(this)
            .setTitle("Change Profile Picture")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    db.child("users").child(auth.uid!!).child("profilePhoto").setValue(url)
                    Picasso.get().load(url).into(profileImage)
                    Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadProfileData() {
        val uid = auth.currentUser?.uid ?: return
        db.child("users").child(uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val username = snapshot.child("username").getValue(String::class.java) ?: "User"
                val profileUrl = snapshot.child("profilePhoto").getValue(String::class.java)
                usernameText.text = username
                profileUrl?.let { if (it.isNotEmpty()) Picasso.get().load(it).into(profileImage) }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun observeUserXP() {
        val uid = auth.currentUser?.uid ?: return
        db.child("users").child(uid).child("xp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val xp = snapshot.getValue(Int::class.java) ?: 0
                val level = xp / 100
                val progress = xp % 100
                xpBar.max = 100
                xpBar.progress = progress
                levelText.text = "Level $level"
                xpText.text = "$progress / 100 XP"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadFriends() {
        val uid = auth.currentUser?.uid ?: return
        friendsContainer.removeAllViews()
        db.child("users").child(uid).child("friends")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    friendsContainer.removeAllViews()
                    for (friendSnap in snapshot.children) {
                        val friendUid = friendSnap.key ?: continue
                        val isFriend = friendSnap.getValue(Boolean::class.java) ?: false
                        if (isFriend) {
                            db.child("users").child(friendUid)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(friendData: DataSnapshot) {
                                        val friendName = friendData.child("username").getValue(String::class.java) ?: "Friend"
                                        val tv = TextView(this@ProfileActivity)
                                        tv.text = friendName
                                        tv.textSize = 16f
                                        tv.setPadding(16, 8, 16, 8)
                                        friendsContainer.addView(tv)
                                    }
                                    override fun onCancelled(error: DatabaseError) {}
                                })
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadFriendRequests() {
        val uid = auth.currentUser?.uid ?: return
        friendRequestsContainer.removeAllViews()
        db.child("users").child(uid).child("friendRequests")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    friendRequestsContainer.removeAllViews()
                    for (reqSnap in snapshot.children) {
                        val requesterUid = reqSnap.key ?: continue
                        val layout = LinearLayout(this@ProfileActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }

                        val nameView = TextView(this@ProfileActivity).apply {
                            text = requesterUid
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val acceptBtn = Button(this@ProfileActivity).apply {
                            text = "Accept"
                            setOnClickListener {
                                db.child("users").child(uid).child("friends").child(requesterUid).setValue(true)
                                db.child("users").child(requesterUid).child("friends").child(uid).setValue(true)
                                db.child("users").child(uid).child("friendRequests").child(requesterUid).removeValue()
                                loadFriends()
                                loadFriendRequests()
                            }
                        }

                        val rejectBtn = Button(this@ProfileActivity).apply {
                            text = "Reject"
                            setOnClickListener {
                                db.child("users").child(uid).child("friendRequests").child(requesterUid).removeValue()
                                loadFriendRequests()
                            }
                        }

                        layout.addView(nameView)
                        layout.addView(acceptBtn)
                        layout.addView(rejectBtn)
                        friendRequestsContainer.addView(layout)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupFriendSearch() {
        searchFriendsInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase()
                if (query.isNotEmpty()) searchAndDisplayUsers(query)
                else {
                    searchResultsContainer.removeAllViews()
                    noResultsText.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun searchAndDisplayUsers(query: String) {
        val uid = auth.currentUser?.uid ?: return
        searchResultsContainer.removeAllViews()
        noResultsText.visibility = View.GONE

        db.child("users")
            .orderByChild("usernameLower")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("ProfileSearch", "Snapshot children: ${snapshot.childrenCount}")
                    var found = false

                    for (userSnap in snapshot.children) {
                        val userId = userSnap.key ?: continue
                        if (userId == uid) continue

                        val userName = userSnap.child("username").getValue(String::class.java) ?: "User"
                        found = true

                        val layout = LinearLayout(this@ProfileActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(16, 8, 16, 8)
                        }

                        val textView = TextView(this@ProfileActivity).apply {
                            text = userName
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val addBtn = Button(this@ProfileActivity).apply {
                            text = "Add"
                            setOnClickListener {
                                db.child("users").child(userId).child("friendRequests").child(uid).setValue(true)
                                Toast.makeText(this@ProfileActivity, "Request sent to $userName", Toast.LENGTH_SHORT).show()
                            }
                        }

                        layout.addView(textView)
                        layout.addView(addBtn)
                        searchResultsContainer.addView(layout)
                    }

                    if (!found) {
                        noResultsText.visibility = View.VISIBLE
                        searchResultsContainer.addView(noResultsText)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileSearch", "Search cancelled: ${error.message}")
                }
            })
    }
}
