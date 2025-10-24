# Batch Translation Usage

This document provides examples of how to use the batch translation feature in Versta.Android.

## Overview

The Marian translation implementation now supports batch translation, which allows you to translate multiple sentences simultaneously. This provides significant performance improvements when translating multiple sentences, as the encoder processes all sentences in parallel.

## API Methods

### `runBatch`

Translates multiple sentences synchronously and returns all results at once.

```kotlin
val inference: TranslationInference = MarianInference(ortEnvironment)

// Prepare multiple sentences
val sentences = arrayOf(
    "Hello, how are you?",
    "The weather is nice today.",
    "I love programming."
)

// Tokenize each sentence
val inputIds = sentences.map { tokenizer.encode(it).first }.toTypedArray()
val attentionMasks = sentences.map { tokenizer.encode(it).second }.toTypedArray()

// Translate all sentences at once
val results = inference.runBatch(
    inputIds = inputIds,
    attentionMask = attentionMasks,
    eosId = tokenizer.eosId,
    padId = tokenizer.padId,
    minP = 0.01f,
    repetitionPenalty = 1.0f,
    beamSize = 4,
    maxSequenceLength = 512
)

// Decode results
val translations = results.map { tokenizer.decode(it) }
```

### `runBatchAsFlow`

Translates multiple sentences and returns a flow that emits intermediate results as each sentence is being translated.

```kotlin
val inference: TranslationInference = MarianInference(ortEnvironment)

// Prepare multiple sentences
val sentences = arrayOf(
    "Hello, how are you?",
    "The weather is nice today.",
    "I love programming."
)

// Tokenize each sentence
val inputIds = sentences.map { tokenizer.encode(it).first }.toTypedArray()
val attentionMasks = sentences.map { tokenizer.encode(it).second }.toTypedArray()

// Translate all sentences with progress updates
inference.runBatchAsFlow(
    inputIds = inputIds,
    attentionMask = attentionMasks,
    eosId = tokenizer.eosId,
    padId = tokenizer.padId,
    minP = 0.01f,
    repetitionPenalty = 1.0f,
    beamSize = 4,
    maxSequenceLength = 512
).collect { partialResults ->
    // partialResults is an Array<LongArray> with the current state of each translation
    val translations = partialResults.map { tokenizer.decode(it) }
    
    // Update UI with partial translations
    translations.forEachIndexed { index, translation ->
        println("Sentence $index: $translation")
    }
}
```

## Performance Benefits

Batch translation provides performance benefits primarily through:

1. **Encoder Batching**: All sentences are encoded in parallel in a single encoder call, which is where most of the computational cost is incurred.

2. **Efficient Resource Usage**: By processing multiple sentences together, the encoder can better utilize available CPU/GPU resources.

3. **Reduced Overhead**: Single encoder call instead of multiple sequential calls reduces function call overhead and memory allocation/deallocation overhead.

## Implementation Details

The current batch implementation:
- Encodes all sentences in parallel using a single encoder call
- Decodes each sentence independently with its own beam search instance
- For `runBatch`: returns all final results synchronously
- For `runBatchAsFlow`: emits progressive updates as each sentence generates tokens

## Use Cases

Batch translation is particularly beneficial for:
- Translating multiple sentences from a document
- Real-time translation of chat conversations
- Batch processing of user-submitted texts
- Any scenario where you have multiple independent sentences to translate

## Example: Translating a Document

```kotlin
suspend fun translateDocument(
    paragraphs: List<String>,
    inference: TranslationInference,
    tokenizer: TranslationTokenizer
): List<String> {
    // Split into batches of 10 sentences
    val batchSize = 10
    val allTranslations = mutableListOf<String>()
    
    paragraphs.chunked(batchSize).forEach { batch ->
        val inputIds = batch.map { tokenizer.encode(it).first }.toTypedArray()
        val attentionMasks = batch.map { tokenizer.encode(it).second }.toTypedArray()
        
        val results = inference.runBatch(
            inputIds = inputIds,
            attentionMask = attentionMasks,
            eosId = tokenizer.eosId,
            padId = tokenizer.padId,
            minP = 0.01f,
            repetitionPenalty = 1.0f,
            beamSize = 4,
            maxSequenceLength = 512
        )
        
        allTranslations.addAll(results.map { tokenizer.decode(it) })
    }
    
    return allTranslations
}
```

## Future Optimizations

For even better performance, future improvements could include:
- Batched decoder processing (currently each sentence is decoded independently)
- Dynamic batching based on sentence lengths
- Parallel beam search across batch dimension in C++
