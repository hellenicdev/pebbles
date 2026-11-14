# ch32v003fun (the ch32fun tree). CI clones it and passes CH32FUN.
CH32FUN ?= $(abspath ../../../ch32v003fun/ch32fun)

# Ubuntu splits the headers out of gcc-riscv64-unknown-elf.
ifneq ($(wildcard /usr/lib/picolibc/riscv64-unknown-elf/include/stdio.h),)
NEWLIB ?= /usr/lib/picolibc/riscv64-unknown-elf/include
endif

ifeq ($(wildcard $(CH32FUN)/ch32fun.mk),)
$(error Set CH32FUN to ch32v003fun/ch32fun)
endif

all : build

# GCC 14 rejects csr instructions unless the zicsr extension is named.
EXTRA_CFLAGS += -march=rv32ec_zicsr -mabi=ilp32e

TARGET := dock
TARGET_MCU := CH32V003

include $(CH32FUN)/ch32fun.mk

clean : cv_clean
