package com.rossomak.flashcards.core.domain.annotation

/**
 * Opts a class or function out of one or more architecture-convention Konsist rules.
 * Always pass a reason; grep for this annotation to see what's exempted and why.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ArchConventionExempt(val reason: String)
