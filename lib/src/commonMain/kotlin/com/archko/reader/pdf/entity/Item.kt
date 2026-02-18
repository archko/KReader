package com.archko.reader.pdf.entity

public class Item(
    public var title: String?,
    public var page: Int,
    public var level: Int = 0,
    public var children: List<Item> = emptyList()
) {
    override fun toString(): String {
        return String.format("%s - %s", page, title)
    }
}