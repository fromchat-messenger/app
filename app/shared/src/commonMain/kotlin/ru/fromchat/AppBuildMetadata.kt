package ru.fromchat

fun AppBuildInfo.appRepoBlobUrl(relativePath: String): String? {
    val repo = githubRepository.trim()
    if (repo.isEmpty()) return null
    val path = relativePath
        .replace('\\', '/')
        .removePrefix("./")
        .trimStart('/')
    if (path.isEmpty()) return null
    val sha = gitCommitSha.trim().lowercase()
    val ref = if (sha.matches(Regex("^[0-9a-f]{7,40}$"))) sha else "main"
    return "https://github.com/$repo/blob/$ref/$path"
}
