cmake_minimum_required(VERSION 3.10)
project(paddleocr)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(PaddleLite_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../PaddleLite")
set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../OpenCV/sdk/native/jni")

include_directories(${PaddleLite_DIR}/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

message(STATUS "PaddleLite: ${PaddleLite_DIR}")
message(STATUS "OpenCV: ${OpenCV_DIR}")

find_package(OpenCV REQUIRED core imgproc)
include_directories(${OpenCV_INCLUDE_DIRS})

add_library(paddleocr SHARED
    det_process.cc
    cls_process.cc
    rec_process.cc
    db_post_process.cc
    utils.cc
    clipper.cpp
    ocr_engine.cc
    jni_bridge.cc
)

find_library(log-lib log)

add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(paddle_light_api_shared PROPERTIES
    IMPORTED_LOCATION
    ${PaddleLite_DIR}/libs/${ANDROID_ABI}/libpaddle_light_api_shared.so
)

target_link_libraries(paddleocr
    paddle_light_api_shared
    ${OpenCV_LIBS}
    jnigraphics
    ${log-lib}
)

target_compile_options(paddleocr PRIVATE
    -O2
    -fexceptions
    -frtti
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections
)

set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

add_custom_command(
    TARGET paddleocr
    POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
        ${PaddleLite_DIR}/libs/${ANDROID_ABI}/libpaddle_light_api_shared.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libpaddle_light_api_shared.so
)
