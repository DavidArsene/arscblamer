# ArscBlamer
ArscBlamer is a library that can parse an Android app's resources.arsc file and extract useful, actionable information about its contents.

Features include:
  - Output all resource configurations and see type, variants, size, null entries, entry count, density, and (optionally) the resource names in that configuration.
  - Output all resource names in resources.arsc and see their private, shared, and proportional sizes as well as the configurations they belong to.
  - Output resources which are "baseless" (have no default value).

### Usage

`build.gradle.kts`:
```kts
repositories {
    maven(url = "https://jitpack.io")
}

dependenceis {
    implementation("com.github.DavidArsene:arscblamer:+")
}
```

# License

```
Copyright 2016 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

# Disclaimer

This is not an official Google product
