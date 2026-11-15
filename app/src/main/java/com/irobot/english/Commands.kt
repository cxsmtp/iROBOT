package com.irobot.english

/** The two walking robots supported by the original "757 R/C" app. */
enum class RobotModel(val label: String) {
    ROBOT_DOG("Robot Dog"),
    WOLF_WARRIOR("Wolf Warrior"),
}

/**
 * One robot command. [d3] carries mode flags (song, study, AI, mute...) and
 * [d4] carries the action number, exactly as the original app sends them.
 */
data class RobotCommand(
    val id: String,
    val label: String,
    val d3: Int,
    val d4: Int,
    val reply: String,
    val phrases: List<String>,
    val models: Set<RobotModel> = RobotModel.entries.toSet(),
    val isMove: Boolean = false,
)

object Commands {
    private val DOG = setOf(RobotModel.ROBOT_DOG)
    private val WOLF = setOf(RobotModel.WOLF_WARRIOR)

    val STOP = RobotCommand("stop", "Stop", 0, 1, "Stopping.",
        listOf("stop", "halt", "freeze", "stay", "enough", "that's enough", "wait"))
    val FORWARD = RobotCommand("forward", "Forward", 0, 2, "Going forward.",
        listOf("forward", "go forward", "walk forward", "move forward", "go ahead", "come here", "come", "walk", "go"),
        isMove = true)
    val BACKWARD = RobotCommand("backward", "Back", 0, 3, "Going back.",
        listOf("backward", "backwards", "go back", "move back", "walk back", "back up", "reverse", "back"),
        isMove = true)
    val LEFT = RobotCommand("left", "Left", 0, 4, "Turning left.",
        listOf("turn left", "go left", "left"), isMove = true)
    val RIGHT = RobotCommand("right", "Right", 0, 5, "Turning right.",
        listOf("turn right", "go right", "right", "write"), isMove = true)

    /** All commands, in the order shown on the action grid. */
    val ALL: List<RobotCommand> = listOf(
        STOP, FORWARD, BACKWARD, LEFT, RIGHT,
        RobotCommand("sit", "Sit", 0, 6, "Sitting down.", listOf("sit down", "sit", "set down")),
        RobotCommand("lie", "Lie down", 0, 7, "Lying down.",
            listOf("lie down", "lay down", "lie", "lay", "down", "get down")),
        RobotCommand("cute", "Act cute", 0, 8, "Being cute!",
            listOf("act cute", "be cute", "cute", "beg", "puppy eyes"), DOG),
        RobotCommand("pee", "Pee", 0, 9, "Oops, pee time.", listOf("pee", "pea", "potty", "toilet"), DOG),
        RobotCommand("march", "March", 0, 9, "Marching!", listOf("march", "march on the spot", "step"), WOLF),
        RobotCommand("hello", "Say hello", 0, 10, "Hello there!",
            listOf("say hello", "hello", "hi", "greet", "greeting", "wave", "shake hands", "paw"), DOG),
        RobotCommand("hello2", "Say hello", 128, 0, "Hello there!",
            listOf("say hello", "hello", "hi", "greet", "greeting", "wave", "shake hands"), WOLF),
        RobotCommand("handstand", "Handstand", 0, 11, "Handstand!",
            listOf("handstand", "hand stand", "stand on your hands", "upside down")),
        RobotCommand("pushups", "Push-ups", 0, 12, "Push-ups, let's go!",
            listOf("push ups", "push-ups", "pushups", "push up", "push-up", "exercise", "workout")),
        RobotCommand("dance", "Dance", 0, 13, "Let's dance!", listOf("dance", "dancing", "party", "boogie")),
        RobotCommand("kungfu", "Kung fu", 0, 14, "Kung fu time!",
            listOf("kung fu", "kungfu", "karate", "fight", "martial arts")),
        RobotCommand("swim", "Swim", 0, 15, "Swimming!", listOf("swim", "swimming")),
        RobotCommand("bless", "Blessing", 0, 16, "Sending blessings.", listOf("blessing", "bless", "pray", "bow"), DOG),
        RobotCommand("charge", "Charge", 0, 16, "Charge!", listOf("charge", "attack", "assault"), WOLF),
        RobotCommand("patrol", "Patrol", 0, 17, "On patrol.", listOf("patrol", "guard", "look around", "explore")),
        RobotCommand("shoot", "Shoot", 8, 0, "Fire!", listOf("shoot", "fire", "combat mode", "combat", "bang"), DOG),
        RobotCommand("shoot2", "Shoot", 0, 10, "Fire!", listOf("shoot", "fire", "combat mode", "combat", "bang"), WOLF),
        RobotCommand("salute", "Salute", 8, 0, "Yes sir!", listOf("salute", "attention"), WOLF),
        RobotCommand("light", "Light", 128, 0, "Lights!", listOf("light", "lights", "lamp", "light on", "light off"), DOG),
        RobotCommand("song", "Song", 1, 0, "Music time.", listOf("sing", "song", "music", "play music", "play a song")),
        RobotCommand("study", "Study", 16, 0, "Study mode.", listOf("study", "learn", "story", "tell a story", "encyclopedia")),
        RobotCommand("ai", "AI", 32, 0, "AI mode.", listOf("ai mode", "a.i.", "artificial intelligence", "smart mode")),
        RobotCommand("mute", "Mute robot", 64, 0, "Muting the robot.",
            listOf("mute", "be quiet", "quiet", "silence", "shut up", "unmute", "sound")),
    )

    fun forModel(model: RobotModel): List<RobotCommand> = ALL.filter { model in it.models }

    /** Commands shown as tiles (movement has its own pad). */
    fun actionsFor(model: RobotModel): List<RobotCommand> =
        forModel(model).filter { !it.isMove && it.id != "stop" }

    /**
     * Finds the command a sentence asks for. Longest matching phrase wins, so
     * "turn left" beats "left" and "sit down" beats "down"; "stop" always wins.
     */
    fun match(text: String, model: RobotModel): RobotCommand? {
        val words = " " + text.lowercase().replace(Regex("[^a-z0-9' -]"), " ")
            .replace(Regex("\\s+"), " ").trim() + " "
        if (words.isBlank()) return null
        var best: RobotCommand? = null
        var bestLen = 0
        for (c in forModel(model)) {
            for (p in c.phrases) {
                if (words.contains(" $p ")) {
                    if (c.id == "stop") return c
                    if (p.length > bestLen) { best = c; bestLen = p.length }
                }
            }
        }
        return best
    }

    /** Tries every candidate transcript from the recognizer, best first. */
    fun matchAny(candidates: List<String>, model: RobotModel): Pair<RobotCommand, String>? {
        for (t in candidates) match(t, model)?.let { return it to t }
        return null
    }
}
