package com.example.poketrivia

import org.junit.Assert.assertEquals
import org.junit.Test

class NameNormalizationTest {
    @Test fun displayNameReplacesHyphens() = assertEquals("Mr mime", "mr-mime".replace('-', ' ').replaceFirstChar(Char::uppercase))
}
