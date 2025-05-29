<div align="center">
  <img src="./src/main/resources/assets/ingame-nether-bedrock-cracker/icon.png" width="200">
</div>
<h1 align="center">Ingame Nether Bedrock Cracker</h1>

![](./example/example.png)

# Build with source
1. Set up enviroment
- Install JDK 21
- Install [rust](https://www.rust-lang.org/tools/install)
- Install LLVM

2. Clone repository
```
git clone https://github.com/tmquan2508/ingame-nether-bedrock-cracker.git
cd ingame-nether-bedrock-cracker
git submodule update --init
```

3. Build jextract
```
cd jextract
./gradlew -Pjdk21_home=$JAVA_HOME -Pllvm_home=$LLVM_HOME clean verify
```

4. Build native lib
```
cd ../
cd src/main/nbc
cargo build --release
```

5. Build the mod
```
cd ../../../
./gradlew build
```
