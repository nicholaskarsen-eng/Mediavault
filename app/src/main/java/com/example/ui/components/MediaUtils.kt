package com.example.ui.components

import com.example.data.database.MediaFile

fun matchMetadataSearch(file: MediaFile, query: String): Boolean {
    if (query.isBlank()) return true
    
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return true
    
    return terms.all { term ->
        when {
            term.startsWith("#") -> {
                val tagSearch = term.removePrefix("#").trim()
                file.tags.split(",").any { it.trim().contains(tagSearch, ignoreCase = true) }
            }
            
            term.contains(":") -> {
                val parts = term.split(":", limit = 2)
                val key = parts[0].lowercase().trim()
                val value = parts[1].trim()
                
                when (key) {
                    "type", "category" -> {
                        file.fileType.contains(value, ignoreCase = true) ||
                        file.category.contains(value, ignoreCase = true)
                    }
                    "app", "source" -> {
                        file.sourceApp.contains(value, ignoreCase = true)
                    }
                    "ext" -> {
                        file.fileName.endsWith(value, ignoreCase = true) ||
                        file.fileType.contains(value, ignoreCase = true)
                    }
                    "tag" -> {
                        file.tags.split(",").any { it.trim().contains(value, ignoreCase = true) }
                    }
                    else -> {
                        file.fileName.contains(term, ignoreCase = true) ||
                        file.tags.contains(term, ignoreCase = true) ||
                        (file.aiSummary ?: "").contains(term, ignoreCase = true) ||
                        file.sourceApp.contains(term, ignoreCase = true)
                    }
                }
            }
            
            term.contains(">") || term.contains("<") -> {
                val isGreater = term.contains(">")
                val operator = if (isGreater) ">" else "<"
                val parts = term.split(operator, limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].lowercase().trim()
                    val valueStr = parts[1].trim()
                    
                    when (key) {
                        "size" -> {
                            val bytes = parseSizeStringToBytes(valueStr)
                            if (bytes != null) {
                                if (isGreater) file.fileSize > bytes else file.fileSize < bytes
                            } else true
                        }
                        else -> {
                            file.fileName.contains(term, ignoreCase = true) ||
                            file.tags.contains(term, ignoreCase = true) ||
                            (file.aiSummary ?: "").contains(term, ignoreCase = true)
                        }
                    }
                } else {
                    file.fileName.contains(term, ignoreCase = true) ||
                    file.tags.contains(term, ignoreCase = true) ||
                    (file.aiSummary ?: "").contains(term, ignoreCase = true)
                }
            }
            
            else -> {
                file.fileName.contains(term, ignoreCase = true) ||
                file.tags.split(",").any { it.trim().contains(term, ignoreCase = true) } ||
                file.category.contains(term, ignoreCase = true) ||
                (file.aiSummary ?: "").contains(term, ignoreCase = true) ||
                file.sourceApp.contains(term, ignoreCase = true)
            }
        }
    }
}

fun parseSizeStringToBytes(sizeStr: String): Long? {
    val clean = sizeStr.lowercase().trim()
    val numberPart = clean.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return null
    return when {
        clean.endsWith("gb") || clean.endsWith("g") -> (numberPart * 1024 * 1024 * 1024).toLong()
        clean.endsWith("mb") || clean.endsWith("m") -> (numberPart * 1024 * 1024).toLong()
        clean.endsWith("kb") || clean.endsWith("k") -> (numberPart * 1024).toLong()
        else -> numberPart.toLong()
    }
}

fun calculateRelevance(file: MediaFile, query: String, tagFreq: Map<String, Int>): Int {
    var score = 0
    
    val fileTags = file.tags.split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        
    fileTags.forEach { t ->
        score += (tagFreq[t] ?: 0) * 8
    }
    
    if (query.isNotEmpty()) {
        val q = query.trim().lowercase()
        
        fileTags.forEach { t ->
            if (t == q) {
                score += 50
            } else if (t.contains(q)) {
                score += 24
            }
        }
        
        val cat = file.category.trim().lowercase()
        if (cat == q) {
            score += 35
        } else if (cat.contains(q)) {
            score += 15
        }
        
        val name = file.fileName.trim().lowercase()
        if (name == q) {
            score += 45
        } else if (name.contains(q)) {
            score += 20
        }
        
        val app = file.sourceApp.trim().lowercase()
        if (app == q) {
            score += 30
        } else if (app.contains(q)) {
            score += 10
        }
        
        val summary = (file.aiSummary ?: "").trim().lowercase()
        if (summary.contains(q)) {
            score += 12
        }
    }
    
    return score
}
