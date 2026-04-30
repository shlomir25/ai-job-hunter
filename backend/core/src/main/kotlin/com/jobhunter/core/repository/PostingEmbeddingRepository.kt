package com.jobhunter.core.repository

import com.jobhunter.core.domain.PostingEmbedding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostingEmbeddingRepository : JpaRepository<PostingEmbedding, Long>
