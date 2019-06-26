package io.yooksi.commons.define;

import io.yooksi.commons.util.AnnotationUtils;
import io.yooksi.commons.validator.BeanValidator;

import java.lang.annotation.*;

/**
 * This annotation is applied to other annotations to indicate
 * that they are custom annotations created by this library and
 * require special processing by {@link BeanValidator} such as
 * message parsing and dynamic level assignment.
 *
 * @see AnnotationUtils#isLibraryAnnotation(Annotation)
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LibraryAnnotation {}
