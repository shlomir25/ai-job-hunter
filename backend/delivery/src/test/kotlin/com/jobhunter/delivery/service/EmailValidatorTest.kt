package com.jobhunter.delivery.service

import com.jobhunter.delivery.config.DeliveryProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EmailValidatorTest {

  @Test
  fun `valid email passes`() {
    val v = EmailValidator(DeliveryProperties(fromAddress = "me@me.com"))
    assertEquals(true, v.isValid("jobs@acme.com"))
  }

  @Test
  fun `denies noreply addresses`() {
    val v = EmailValidator(DeliveryProperties(fromAddress = "me@me.com"))
    assertEquals(false, v.isValid("noreply@indeed.com"))
    assertEquals(false, v.isValid("DoNotReply@whatever.com"))
  }

  @Test
  fun `denies sending to self`() {
    val v = EmailValidator(DeliveryProperties(fromAddress = "me@me.com"))
    assertEquals(false, v.isValid("me@me.com"))
  }

  @Test
  fun `rejects malformed`() {
    val v = EmailValidator(DeliveryProperties(fromAddress = "me@me.com"))
    assertEquals(false, v.isValid("not-an-email"))
    assertEquals(false, v.isValid(""))
  }
}
