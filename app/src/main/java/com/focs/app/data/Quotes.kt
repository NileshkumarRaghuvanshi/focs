package com.focs.app.data

import android.content.Context

/** Bundled one-liners, shown alongside the user's own goal lines. Works fully offline. */
object Quotes {
    val lines = listOf(
        "You don't have to feel like it. You just have to start.",
        "Five minutes. Just do five minutes, then decide.",
        "Discipline is choosing what you want most over what you want now.",
        "The scroll will still be there. This moment won't.",
        "Small progress is still progress.",
        "Done is better than perfect.",
        "Future you is watching. Make them proud.",
        "Motivation follows action, not the other way round.",
        "One focused hour beats a distracted day.",
        "You've done hard things before. This is one more.",
        "Win the next ten minutes.",
        "The feeling of lazy passes. The work you did stays.",
        "Don't break the chain today.",
        "Your phone can wait. Your goals can't.",
        "Start ugly. Fix it later.",
        "Every expert was once a beginner who didn't quit.",
        "Comfort is the enemy of progress.",
        "Do it tired. Do it bored. Just do it.",
        "Nobody is coming to do this for you.",
        "A year from now you'll wish you had started today.",
        "Focus on the step in front of you, not the whole staircase.",
        "You are what you repeatedly do.",
        "Hard now, easy later. Easy now, hard later.",
        "The best time was yesterday. The next best is right now.",
        "Be stronger than your excuses.",
        "What you do today decides who you are tomorrow.",
        "Feel the resistance, then do it anyway.",
        "Your attention is the most valuable thing you own.",
        "Make the next move. Then the one after that.",
        "Consistency beats intensity.",
    )

    fun randomLine(): String = lines.random()

    /** Prefers the user's own goals (2 out of 3 times) because those hit harder than generic lines. */
    fun nudge(ctx: Context): Pair<String, Boolean> {
        val goals = Store.goals(ctx)
        return if (goals.isNotEmpty() && Math.random() < 0.67) goals.random() to true else randomLine() to false
    }
}
