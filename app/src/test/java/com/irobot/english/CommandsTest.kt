package com.irobot.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandsTest {
    private fun id(text: String, model: RobotModel = RobotModel.ROBOT_DOG) = Commands.match(text, model)?.id

    @Test fun basicPhrases() {
        assertEquals("sit", id("sit down"))
        assertEquals("lie", id("Lie down please"))
        assertEquals("dance", id("Can you dance?"))
        assertEquals("pushups", id("do some push-ups"))
        assertEquals("kungfu", id("show me kung fu"))
        assertEquals("forward", id("go forward"))
        assertEquals("backward", id("go back"))
        assertEquals("mute", id("be quiet"))
    }

    @Test fun longestPhraseWins() {
        assertEquals("left", id("turn left"))
        assertEquals("sit", id("sit down"))   // not "down" -> lie
    }

    @Test fun stopAlwaysWins() = assertEquals("stop", id("stop dancing"))

    @Test fun unknownIsNull() {
        assertNull(id("what's the weather"))
        assertNull(id(""))
    }

    @Test fun modelSpecificCodes() {
        val dogHello = Commands.match("hello", RobotModel.ROBOT_DOG)!!
        assertEquals(0 to 10, dogHello.d3 to dogHello.d4)
        val wolfHello = Commands.match("hello", RobotModel.WOLF_WARRIOR)!!
        assertEquals(128 to 0, wolfHello.d3 to wolfHello.d4)
        assertEquals("salute", id("salute", RobotModel.WOLF_WARRIOR))
        assertNull(id("salute", RobotModel.ROBOT_DOG))
    }

    @Test fun picksFirstMatchingCandidate() {
        val r = Commands.matchAny(listOf("sat dawn", "sit down"), RobotModel.ROBOT_DOG)!!
        assertEquals("sit", r.first.id)
        assertEquals("sit down", r.second)
    }
}
