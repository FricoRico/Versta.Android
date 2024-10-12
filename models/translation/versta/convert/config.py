from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
from typing import List

def get_source_language(model_name: str) -> str:
    """
    Extracts the source language from the model name.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    tokenizer_config = tokenizer.init_kwargs

    return tokenizer_config.get('source_lang')

def get_target_language(model_name: str) -> str:
    """
    Extracts the target language from the model name.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    tokenizer_config = tokenizer.init_kwargs

    return tokenizer_config.get('target_lang')

def get_architecture(model_name: str) -> List[str]:
    """
    Extracts the architecture from the model name.
    """
    model = AutoModelForSeq2SeqLM.from_pretrained(model_name)
    config = model.config

    return config.architectures