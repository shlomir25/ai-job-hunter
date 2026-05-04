package com.jobhunter.matching.service

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class CvParseService {
  fun extract(bytes: ByteArray, mimeType: String?, fileName: String): String {
    val handler = BodyContentHandler(-1)
    val metadata = Metadata().apply {
      set("resourceName", fileName)
      if (mimeType != null) set(Metadata.CONTENT_TYPE, mimeType)
    }
    val parser = AutoDetectParser()
    ByteArrayInputStream(bytes).use { input ->
      parser.parse(input, handler, metadata, ParseContext())
    }
    return handler.toString().trim()
  }
}
