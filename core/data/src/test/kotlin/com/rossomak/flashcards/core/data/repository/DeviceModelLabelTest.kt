package com.rossomak.flashcards.core.data.repository

import io.kotest.matchers.shouldBe
import org.junit.Test

class DeviceModelLabelTest {

    @Test
    fun `prepends the manufacturer when the model lacks it`() {
        deviceModelLabel(manufacturer = "Google", model = "Pixel 8") shouldBe "Google Pixel 8"
    }

    @Test
    fun `capitalises a lowercase manufacturer`() {
        deviceModelLabel(manufacturer = "samsung", model = "SM-S918B") shouldBe "Samsung SM-S918B"
    }

    @Test
    fun `keeps the model alone when it already starts with the manufacturer`() {
        deviceModelLabel(manufacturer = "HTC", model = "HTC One") shouldBe "HTC One"
    }

    @Test
    fun `matches the manufacturer prefix ignoring case`() {
        deviceModelLabel(manufacturer = "HUAWEI", model = "Huawei P30") shouldBe "Huawei P30"
    }

    @Test
    fun `returns the model alone when the manufacturer is blank`() {
        deviceModelLabel(manufacturer = "  ", model = "Pixel 8") shouldBe "Pixel 8"
    }

    @Test
    fun `returns the manufacturer alone when the model is blank`() {
        deviceModelLabel(manufacturer = "samsung", model = "") shouldBe "Samsung"
    }

    @Test
    fun `treats an unknown manufacturer as blank`() {
        deviceModelLabel(manufacturer = "unknown", model = "sdk_gphone64") shouldBe "sdk_gphone64"
    }

    @Test
    fun `falls back to a placeholder when the manufacturer is unknown and the model blank`() {
        deviceModelLabel(manufacturer = "Unknown", model = "") shouldBe "Unknown device"
    }

    @Test
    fun `falls back to a placeholder when both are blank or missing`() {
        deviceModelLabel(manufacturer = null, model = " ") shouldBe "Unknown device"
    }
}
