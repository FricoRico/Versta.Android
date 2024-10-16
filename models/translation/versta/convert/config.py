from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
from typing import List
from pycountry import languages

def get_source_language(model_name: str) -> str:
    """
    Extracts the source language from the model name.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    tokenizer_config = tokenizer.init_kwargs

    language_code = tokenizer_config.get('source_lang')
    return normalize_language_code(language_code)

def get_target_language(model_name: str) -> str:
    """
    Extracts the target language from the model name.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    tokenizer_config = tokenizer.init_kwargs

    language_code = tokenizer_config.get('target_lang')
    return normalize_language_code(language_code)

def get_architecture(model_name: str) -> List[str]:
    """
    Extracts the architecture from the model name.

    Args:
        model_name (str): Name of the model to extract the architecture from.
    """
    model = AutoModelForSeq2SeqLM.from_pretrained(model_name)
    config = model.config

    return config.architectures

def normalize_language_code(language_code: str) -> str:
    """
    Normalizes the language code to a standard format.

    Args:
        language_code (str): Language code to normalize.
    """
    if len(language_code) == 2:
        return language_code

    try:
        locale = languages.get(alpha_3=language_code)

        if hasattr(locale, 'alpha_2'):
            return locale.alpha_2
        else:
            return None
    except KeyError:
        return None