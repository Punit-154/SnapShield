import onnx
from onnxruntime.quantization import quantize_dynamic, QuantType

print("Quantizing model to reduce size...")
quantize_dynamic(
    "sms_classifier.onnx",
    "sms_classifier_quantized.onnx",
    weight_type=QuantType.QInt8
)
print("Done! sms_classifier_quantized.onnx")