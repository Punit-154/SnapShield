from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

MODEL_NAME = "mrm8488/bert-tiny-finetuned-sms-spam-detection"

print("Downloading model...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
model = AutoModelForSequenceClassification.from_pretrained(
    MODEL_NAME,
    attn_implementation="eager"
)
model.eval()

print("Exporting to ONNX...")
dummy_input = tokenizer(
    "test message",
    return_tensors="pt",
    padding="max_length",
    max_length=128,
    truncation=True
)

with torch.no_grad():
    torch.onnx.export(
        model,
        (dummy_input["input_ids"], dummy_input["attention_mask"]),
        "sms_classifier.onnx",
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={"input_ids": {0: "batch"}, "attention_mask": {0: "batch"}},
        opset_version=14
    )

tokenizer.save_pretrained("sms_spam_model")
print("Done! sms_classifier.onnx + sms_spam_model/vocab.txt")