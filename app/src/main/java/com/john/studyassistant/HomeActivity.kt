package com.john.studyassistant

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.prolificinteractive.materialcalendarview.*
import com.prolificinteractive.materialcalendarview.spans.DotSpan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.random.Random

class HomeActivity : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var todayContainer: LinearLayout
    private lateinit var upcomingContainer: LinearLayout
    private lateinit var recentContainer: LinearLayout
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var db: DatabaseReference
    private val auth = FirebaseAuth.getInstance()
    private val uid: String get() = auth.currentUser?.uid ?: ""

    private val dailyXpLimit = 100L
    private val xpPerTask = 10L

    private fun randomVibrantColor(): Int {
        val hsv = floatArrayOf(Random.nextFloat() * 360f, 0.9f, 1.0f)
        return Color.HSVToColor(hsv)
    }

    private val todayDecorator = object : DayViewDecorator {
        private val today = CalendarDay.today()
        override fun shouldDecorate(day: CalendarDay) = day == today
        override fun decorate(view: DayViewFacade) {
            view.setBackgroundDrawable(
                android.graphics.drawable.ShapeDrawable(
                    android.graphics.drawable.shapes.OvalShape()
                ).apply { paint.color = Color.parseColor("#FFE0F0") }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        db = FirebaseDatabase.getInstance(
            "https://study20070831-default-rtdb.europe-west1.firebasedatabase.app/"
        ).reference

        calendarView = findViewById(R.id.calendarView)
        todayContainer = findViewById(R.id.todayContainer)
        upcomingContainer = findViewById(R.id.upcomingContainer)
        recentContainer = findViewById(R.id.recentContainer)
        bottomNav = findViewById(R.id.bottomNav)

        calendarView.state().edit().setFirstDayOfWeek(Calendar.SUNDAY).commit()
        calendarView.addDecorator(todayDecorator)

        calendarView.setOnDateChangedListener { _, date, _ ->
            val formatted = formatDate(date.year, date.month + 1, date.day)
            showEventDialog(formatted)
        }

        setupBottomNavigation()

        loadTodaysTasks()
        loadUpcomingExams()
        loadRecentDecks()
    }

    private fun setupBottomNavigation() {
        bottomNav.selectedItemId = R.id.home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> true
                R.id.add -> {
                    startActivity(Intent(this, AddActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
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

    private fun formatDate(year: Int, month: Int, day: Int) =
        String.format("%04d-%02d-%02d", year, month, day)

    private fun showEventDialog(date: String) {
        val examsRef = db.child("users").child(uid).child("exams").child(date)
        val revisionsRef = db.child("users").child(uid).child("revisions").child(date)

        val options = mutableListOf("Add Exam", "Add Revision Task")
        examsRef.get().addOnSuccessListener { examSnap ->
            revisionsRef.get().addOnSuccessListener { revSnap ->
                if (examSnap.exists() || revSnap.exists()) options.add("Delete Entries")
                AlertDialog.Builder(this)
                    .setTitle("Manage Events for $date")
                    .setItems(options.toTypedArray()) { _, which ->
                        when (options[which]) {
                            "Add Exam" -> addExamDialog(date)
                            "Add Revision Task" -> addRevisionDialog(date)
                            "Delete Entries" -> deleteEntriesDialog(date, examSnap, revSnap)
                        }
                    }.show()
            }
        }
    }

    private fun addExamDialog(date: String) {
        val input = EditText(this)
        input.hint = "Enter exam (e.g. Math Paper 1)"
        AlertDialog.Builder(this)
            .setTitle("Add Exam")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val examText = input.text.toString().trim()
                if (uid.isEmpty() || examText.isEmpty()) return@setPositiveButton
                val examObj = mapOf("text" to examText, "timestamp" to ServerValue.TIMESTAMP)
                db.child("users").child(uid).child("exams").child(date).push().setValue(examObj)
                    .addOnSuccessListener { loadUpcomingExams() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addRevisionDialog(date: String) {
        val input = EditText(this)
        input.hint = "Enter revision task"
        AlertDialog.Builder(this)
            .setTitle("Add Revision Task")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val taskText = input.text.toString().trim()
                if (uid.isEmpty() || taskText.isEmpty()) return@setPositiveButton
                val taskObj = mapOf("text" to taskText, "completed" to false, "timestamp" to ServerValue.TIMESTAMP)
                db.child("users").child(uid).child("revisions").child(date).push().setValue(taskObj)
                    .addOnSuccessListener { loadUpcomingExams() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEntriesDialog(date: String, examSnap: DataSnapshot, revSnap: DataSnapshot) {
        val allItems = mutableListOf<Pair<String, String>>()
        examSnap.children.forEach { allItems.add(it.key!! to "Exam") }
        revSnap.children.forEach { allItems.add(it.key!! to "Revision") }

        val names = allItems.map { "${it.second}: ${it.first}" }.toTypedArray()
        val checked = BooleanArray(names.size)
        AlertDialog.Builder(this)
            .setTitle("Select entries to delete")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Delete") { _, _ ->
                allItems.forEachIndexed { index, (key, type) ->
                    if (checked[index]) {
                        val ref = if (type == "Exam")
                            db.child("users").child(uid).child("exams").child(date).child(key)
                        else
                            db.child("users").child(uid).child("revisions").child(date).child(key)
                        ref.removeValue().addOnSuccessListener { loadUpcomingExams() }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadTodaysTasks() {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val revisionsRef = db.child("users").child(uid).child("revisions").child(today)
        revisionsRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                todayContainer.removeAllViews()
                if (!snapshot.exists()) {
                    val tv = TextView(this@HomeActivity)
                    tv.text = "No revision tasks for today."
                    tv.setPadding(16, 16, 16, 16)
                    todayContainer.addView(tv)
                    return
                }
                snapshot.children.forEach { taskSnap ->
                    val key = taskSnap.key ?: return@forEach
                    val taskText = taskSnap.child("text").getValue(String::class.java) ?: ""
                    val completed = taskSnap.child("completed").getValue(Boolean::class.java) ?: false
                    createTaskCard(today, key, taskText, completed)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun createTaskCard(date: String, taskKey: String, taskText: String, completed: Boolean) {
        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(8, 8, 8, 8)
            layoutParams = lp
            radius = 16f
            cardElevation = 6f
            setCardBackgroundColor(Color.WHITE)
            setContentPadding(16, 12, 16, 12)
        }

        val checkBox = CheckBox(this).apply {
            text = taskText
            isChecked = completed
            textSize = 15f
            paint.isStrikeThruText = completed
            setTextColor(Color.BLACK) // Black text
        }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            db.child("users").child(uid).child("revisions").child(date).child(taskKey).child("completed")
                .setValue(isChecked)
            if (isChecked) grantXpIfNotCapped()
        }

        card.addView(checkBox)
        todayContainer.addView(card)
    }

    private fun grantXpIfNotCapped() {
        val today = LocalDate.now().toString()
        val xpRef = db.child("users").child(uid).child("xp")
        val dailyRef = db.child("users").child(uid).child("xpDates").child(today)

        dailyRef.get().addOnSuccessListener { dailySnap ->
            val dailyTotal = dailySnap.getValue(Long::class.java) ?: 0L
            if (dailyTotal < dailyXpLimit) {
                val xpToAdd = minOf(xpPerTask, dailyXpLimit - dailyTotal)
                xpRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentXp = currentData.getValue(Long::class.java) ?: 0L
                        currentData.value = currentXp + xpToAdd
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (committed) dailyRef.setValue(dailyTotal + xpToAdd)
                    }
                })
            }
        }
    }

    private fun loadRecentDecks() {
        val uid = auth.currentUser?.uid ?: return
        val recentRef = db.child("users").child(uid).child("recentDecks")

        recentRef.orderByValue().limitToLast(2).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val recentDecks = snapshot.children
                    .sortedByDescending { it.getValue(Long::class.java) ?: 0L }

                recentContainer.removeAllViews()

                for (deckSnapshot in recentDecks) {
                    val deckId = deckSnapshot.key ?: continue

                    // Fetch deck title from decks node
                    db.child("decks").child(deckId).child("title")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(titleSnapshot: DataSnapshot) {
                                val title = titleSnapshot.getValue(String::class.java) ?: "Unknown Deck"

                                // Create a TextView for each recent deck
                                val tv = TextView(this@HomeActivity).apply {
                                    text = "• $title"
                                    textSize = 16f
                                    setPadding(0, 8, 0, 8)
                                    setTextColor(Color.BLACK)
                                    setOnClickListener {
                                        // Open StudyFlashcardsActivity
                                        val intent = Intent(this@HomeActivity, StudyFlashcardsActivity::class.java)
                                        intent.putExtra("deckId", deckId)
                                        startActivity(intent)
                                    }
                                }

                                recentContainer.addView(tv)
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadUpcomingExams() {
        val examsRef = db.child("users").child(uid).child("exams")
        val revisionsRef = db.child("users").child(uid).child("revisions")

        examsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(examSnapshot: DataSnapshot) {
                upcomingContainer.removeAllViews()
                calendarView.removeDecorators()
                calendarView.addDecorator(todayDecorator)

                val todayDate = LocalDate.now()
                val examList = mutableListOf<Pair<LocalDate, DataSnapshot>>()
                val dateColors = mutableMapOf<String, Int>() // same color per date

                // Collect all exams
                examSnapshot.children.forEach { dateSnap ->
                    val dateKey = dateSnap.key ?: return@forEach
                    val examDate = try { LocalDate.parse(dateKey) } catch (_: Exception) { return@forEach }
                    if (examDate.isBefore(todayDate)) return@forEach
                    examList.add(examDate to dateSnap)
                }

                val sortedExams = examList.sortedBy { it.first }

                // Generate a consistent color per date
                sortedExams.forEach { (examDate, _) ->
                    dateColors.putIfAbsent(examDate.toString(), randomVibrantColor())
                }

                // Display top 4 in upcomingContainer with colored bullet (bullet only colored)
                sortedExams.take(4).forEach { (examDate, dateSnap) ->
                    val color = dateColors[examDate.toString()] ?: Color.BLACK

                    dateSnap.children.forEach { examSnapChild ->
                        val examText = examSnapChild.child("text").getValue(String::class.java) ?: return@forEach
                        val formattedDate = examDate.format(DateTimeFormatter.ofPattern("d MMM"))

                        val displayText = "\u2022 $formattedDate - $examText"
                        val spannable = SpannableString(displayText)
                        spannable.setSpan(
                            ForegroundColorSpan(color),
                            0, 1,
                            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        val tv = TextView(this@HomeActivity).apply {
                            text = spannable
                            textSize = 15f
                            setPadding(8, 8, 8, 8)
                            setTextColor(Color.BLACK) // exam text remains black
                        }

                        upcomingContainer.addView(tv)
                    }
                }


                sortedExams.forEach { (examDate, _) ->
                    val color = dateColors[examDate.toString()] ?: randomVibrantColor()
                    val calDay = CalendarDay.from(examDate.year, examDate.monthValue - 1, examDate.dayOfMonth)
                    calendarView.addDecorator(object : DayViewDecorator {
                        override fun shouldDecorate(day: CalendarDay): Boolean = day == calDay
                        override fun decorate(view: DayViewFacade) {
                            view.addSpan(DotSpan(10f, color))
                        }
                    })
                }


                revisionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(revSnapshot: DataSnapshot) {
                        revSnapshot.children.forEach { dateSnap ->
                            val revDate = try { LocalDate.parse(dateSnap.key!!) } catch (_: Exception) { return@forEach }
                            if (revDate.isBefore(todayDate)) return@forEach
                            val calDay = CalendarDay.from(revDate.year, revDate.monthValue - 1, revDate.dayOfMonth)
                            calendarView.addDecorator(object : DayViewDecorator {
                                override fun shouldDecorate(day: CalendarDay): Boolean = day == calDay
                                override fun decorate(view: DayViewFacade) {
                                    view.addSpan(DotSpan(6f, Color.GRAY))
                                }
                            })
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
