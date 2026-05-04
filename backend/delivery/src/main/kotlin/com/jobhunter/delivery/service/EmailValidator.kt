package com.jobhunter.delivery.service

import com.jobhunter.delivery.config.DeliveryProperties
import org.springframework.stereotype.Component

@Component
class EmailValidator(private val props: DeliveryProperties) {

  fun isValid(toAddress: String): Boolean {
    if (!EMAIL_REGEX.matches(toAddress)) return false
    if (toAddress.equals(props.fromAddress, ignoreCase = true)) return false
    val lower = toAddress.lowercase()
    if (props.denyList.any { lower.contains(it.lowercase()) }) return false
    return true
  }

  companion object {
    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
  }
}
