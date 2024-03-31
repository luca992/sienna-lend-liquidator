# sienna-lend-liquidator.kt

### Configuration

1. Create a CoinGecko API key [here](https://support.coingecko.com/hc/en-us/articles/21880397454233-User-Guide-How-to-sign-up-for-CoinGecko-Demo-API-and-generate-an-API-key)
2. Copy `config.example.json` to `config.json` and fill your mnemonic, coingecko api key, and add viewing keys for the markets and underlying assets you want to liquidate.



* Note: JDK 17 is required to build

### Running

```
./gradlew run
```
