# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

JNI_SRC_FILES := \
    com_github_google_sentencepiece_SentencePieceJNI.cc

LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/sentencepiece/builtin_pb
LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/third_party
LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/third_party/protobuf-lite
LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/third_party/esaxx
LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/third_party/darts_clone
LOCAL_C_INCLUDES += $(LOCAL_PATH)/src/third_party/absl

CORE_SRC_FILES := \
	third_party/absl/flags/flag.cc \
	third_party/protobuf-lite/arena.cc \
    third_party/protobuf-lite/arenastring.cc \
    third_party/protobuf-lite/bytestream.cc \
    third_party/protobuf-lite/coded_stream.cc \
    third_party/protobuf-lite/common.cc \
    third_party/protobuf-lite/extension_set.cc \
    third_party/protobuf-lite/generated_enum_util.cc \
    third_party/protobuf-lite/generated_message_table_driven_lite.cc \
    third_party/protobuf-lite/generated_message_util.cc \
    third_party/protobuf-lite/implicit_weak_message.cc \
    third_party/protobuf-lite/int128.cc \
    third_party/protobuf-lite/io_win32.cc \
    third_party/protobuf-lite/message_lite.cc \
    third_party/protobuf-lite/parse_context.cc \
    third_party/protobuf-lite/repeated_field.cc \
    third_party/protobuf-lite/status.cc \
    third_party/protobuf-lite/statusor.cc \
    third_party/protobuf-lite/stringpiece.cc \
    third_party/protobuf-lite/stringprintf.cc \
    third_party/protobuf-lite/structurally_valid.cc \
    third_party/protobuf-lite/strutil.cc \
    third_party/protobuf-lite/time.cc \
    third_party/protobuf-lite/wire_format_lite.cc \
    third_party/protobuf-lite/zero_copy_stream.cc \
    third_party/protobuf-lite/zero_copy_stream_impl.cc \
    third_party/protobuf-lite/zero_copy_stream_impl_lite.cc \
    sentencepiece/builtin_pb/sentencepiece_model.pb.cc \
    sentencepiece/builtin_pb/sentencepiece.pb.cc \
    $(addprefix sentencepiece/, \
        bpe_model.cc \
        char_model.cc \
        error.cc \
        filesystem.cc \
        model_factory.cc \
        model_interface.cc \
        normalizer.cc \
        sentencepiece_processor.cc \
        unigram_model.cc \
        util.cc \
        word_model.cc)
