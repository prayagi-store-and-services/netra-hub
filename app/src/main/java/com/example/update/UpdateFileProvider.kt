package com.example.update

import androidx.core.content.FileProvider

/** Separate FileProvider so update APKs never share paths with the app's other providers. */
class UpdateFileProvider : FileProvider()
