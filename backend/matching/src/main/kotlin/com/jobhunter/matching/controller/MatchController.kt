package com.jobhunter.matching.controller

import com.jobhunter.matching.dto.MatchView
import com.jobhunter.matching.service.MatchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/matches")
class MatchController(private val service: MatchService) {

  @GetMapping
  fun list(): List<MatchView> = service.listReady()

  @GetMapping("/{id}")
  fun get(
    @PathVariable id: Long,
  ): ResponseEntity<MatchView> =
    service.get(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}
