# Gemma-4-E4B-it Model Conversion Guide

This document describes how to convert the Gemma-4-E4B-it model to TFLite int4 format for use in the SMSentry Android app.

## Prerequisites

- Python 3.10+
- pip package manager
- ~10GB free disk space
- GPU recommended for conversion (CPU-only is possible but slow)

## Step 1: Install Dependencies

```bash
pip install ai-edge-torch
pip install tensorflow
pip install transformers
```

## Step 2: Download the Model

```bash
# Using Hugging Face CLI
pip install huggingface_hub
huggingface-cli download google/gemma-4-E4B-it --local-dir ./gemma-4-e4b-it
```

Or download manually from: https://huggingface.co/google/gemma-4-E4B-it

## Step 3: Convert to TFLite

```bash
python -m ai_edge_torch.convert \
  --model_path ./gemma-4-e4b-it \
  --output ./gemma-4-e4b-it-int4.tflite \
  --quantization int4 \
  --tokenizer_output ./tokenizer.json
```

### Expected Output Files

| File | Size | Description |
|------|------|-------------|
| `gemma-4-e4b-it-int4.tflite` | ~2.5-3 GB | Quantized model weights |
| `tokenizer.json` | ~5 MB | Tokenizer configuration |

## Step 4: Verify the Model

```bash
# Test with a simple prompt
python -c "
import tensorflow as tf
interpreter = tf.lite.Interpreter(model_path='gemma-4-e4b-it-int4.tflite')
print('Model loaded successfully')
print('Input details:', interpreter.get_input_details())
print('Output details:', interpreter.get_output_details())
"
```

## Step 5: Deploy to App

1. Copy `tokenizer.json` to `app/src/main/assets/tokenizer.json`
2. The `.tflite` file is downloaded on-demand by the app (too large for APK)

## Troubleshooting

### Conversion Fails with Memory Error
- Reduce batch size or use GPU with more VRAM
- Try conversion on a machine with 32GB+ RAM

### Model Too Large
- Verify int4 quantization was applied
- Check file size is between 2.5-3 GB

### Tokenizer Not Found
- Ensure `transformers` package is installed
- Try downloading tokenizer separately from HuggingFace

## Model Specifications

| Property | Value |
|----------|-------|
| Base Model | Gemma-4-E4B-it |
| Quantization | int4 |
| Parameters | ~4 billion |
| Context Window | 32,768 tokens |
| Max Output | 384 tokens (configured in app) |
| License | Google Gemma License |

## Version History

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2025-01 | Initial conversion |

## References

- [Gemma-4 Documentation](https://ai.google.dev/gemma/docs)
- [ai-edge-torch GitHub](https://github.com/google-ai-edge/ai-edge-torch)
- [LiteRT-LM Documentation](https://ai.google.dev/edge/litert/litertlm)
