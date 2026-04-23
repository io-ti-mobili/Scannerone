package com.example.scannerone.database

data class StatCount(
    val type: String?,
    val count: Int
)

data class StatCountFloat(
    val type: Float?,
    val count: Int
)

data class BucketStat(
    val bucketIndex: Int,
    val count: Int
)
