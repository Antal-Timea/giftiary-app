package app.giftiary.domain.model

/**
 * What saving a form does to a row's photo. [Unchanged] keeps whatever photo the row names when
 * the save is written, which another device may have changed since the form was opened.
 */
sealed interface PhotoEdit {
    data object Unchanged : PhotoEdit

    /** [path]: a [app.giftiary.domain.repository.GiftPhotoRepository] path. */
    data class Replace(val path: String) : PhotoEdit

    data object Remove : PhotoEdit
}
