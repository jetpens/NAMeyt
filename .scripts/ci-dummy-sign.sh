#!/usr/bin/env bash

mkdir build
mkdir build/output

cp mirai-login-solver-sakura/build/mirai/* build/output

apksigner sign -v --key keys/ci-dummy.key.pkcs8 --c