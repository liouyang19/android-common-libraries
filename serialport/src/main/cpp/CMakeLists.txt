cmake_minimum_required(VERSION 3.22.1)

project("serial_port")

add_library(${CMAKE_PROJECT_NAME} SHARED
        SerialPort.cpp)
