package helpers

object MaterialHelper {
  import views.html.helper.FieldConstructor
  implicit val myFields = FieldConstructor(views.html.components.input.f)
}
