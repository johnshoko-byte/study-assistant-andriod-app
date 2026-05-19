package com.john.studyassistant

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream

class UserDetailsActivity : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private lateinit var spinnerLayout: LinearLayout
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var gradeSpinner: Spinner
    private val PICK_IMAGE_REQUEST = 100
    private var selectedImageUri: Uri? = null

    private val subjects = listOf(
        "English HL", "English FAL", "Afrikaans HL", "Afrikaans FAL",
        "IsiZulu HL", "IsiZulu FAL", "IsiXhosa HL", "IsiXhosa FAL",
        "Sepedi HL", "Sepedi FAL", "Setswana HL", "Setswana FAL",
        "Sesotho HL", "Sesotho FAL", "Xitsonga HL", "Xitsonga FAL",
        "Mathematics", "Mathematical Literacy", "Physical Sciences", "Life Sciences",
        "History", "Geography", "Economics", "Business Studies", "Accounting",
        "Information Technology", "Computer Applications Technology",
        "Music", "Visual Arts", "Drama",
        "Life Orientation", "Engineering Graphics and Design"
    )

    private val grades = listOf("8","9","10","11","12")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_details)

        profileImageView = findViewById(R.id.profileImageView)
        spinnerLayout = findViewById(R.id.spinnerLayout)
        val addButton = findViewById<Button>(R.id.btnAddSubject)
        val saveButton = findViewById<Button>(R.id.btnSave)
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val selectPhotoButton = findViewById<Button>(R.id.btnSelectProfilePhoto)
        gradeSpinner = findViewById(R.id.gradeSpinner)

        // Subjects spinner adapter
        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, subjects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // Add first 7 spinners by default
        for (i in 1..7) addSpinner()

        addButton.setOnClickListener { addSpinner() }
        selectPhotoButton.setOnClickListener { openGallery() }

        // Grade spinner setup
        val gradeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, grades)
        gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gradeSpinner.adapter = gradeAdapter

        // Load profile photo if exists
        loadProfilePhoto()

        saveButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val grade = gradeSpinner.selectedItem.toString()

            if (username.length < 3) {
                Toast.makeText(this, "Username must be at least 3 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val chosenSubjects = mutableListOf<String>()
            for (i in 0 until spinnerLayout.childCount) {
                val spinner = spinnerLayout.getChildAt(i) as Spinner
                chosenSubjects.add(spinner.selectedItem.toString())
            }

            if (chosenSubjects.size < 7) {
                Toast.makeText(this, "Pick at least 7 subjects", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (chosenSubjects.toSet().size != chosenSubjects.size) {
                Toast.makeText(this, "Subjects must be unique", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveUserDetails(username, grade, chosenSubjects)
        }
    }

    private fun addSpinner() {
        val spinner = Spinner(this)
        spinner.adapter = adapter
        spinnerLayout.addView(spinner)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            profileImageView.setImageURI(selectedImageUri)
        }
    }

    private fun uriToBase64(uri: Uri): String? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream) // compress
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun saveUserDetails(username: String, grade: String, subjects: List<String>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val dbRef = FirebaseDatabase.getInstance(
            "https://study20070831-default-rtdb.europe-west1.firebasedatabase.app/"
        ).getReference("users").child(userId)

        val userData = hashMapOf<String, Any>()
        userData["username"] = username
        userData["grade"] = grade

        // convert list to map with numeric keys
        val subjectsMap = subjects.mapIndexed { index, s -> index.toString() to s }.toMap()
        userData["subjects"] = subjectsMap

        selectedImageUri?.let {
            val base64Image = uriToBase64(it)
            base64Image?.let { img -> userData["profilePhoto"] = img }
        }

        dbRef.updateChildren(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Details saved!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("FirebaseError", e.message ?: "Unknown error")
            }
    }

    private fun loadProfilePhoto() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance(
            "https://study20070831-default-rtdb.europe-west1.firebasedatabase.app/"
        ).getReference("users").child(userId).child("profilePhoto")

        ref.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val base64 = snapshot.getValue(String::class.java)
                base64?.let {
                    val bytes = Base64.decode(it, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    profileImageView.setImageBitmap(bitmap)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
