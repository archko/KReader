package com.archko.reader.pdf.entity

public class Item(public var title: String?, public var page: Int) {
    override fun toString(): String {
        return String.format("%s - %s", page, title)
    }
}