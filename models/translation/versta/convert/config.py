from transformers import AutoTokenizer

def get_source_language(model_name: str):
    """
    Extracts the source language from the model name.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    tokenizer_config = tokenizer.init_kwargs

    return tokenizer_config.get('source_lang')

def get_target_language(model_name: str):
    """
    Extracts the target language from the model name.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    tokenizer_config = tokenizer.init_kwargs

    return tokenizer_config.get('target_lang')
