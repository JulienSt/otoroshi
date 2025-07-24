package otoroshi.openapi

import play.api.libs.json._

import scala.collection.concurrent.TrieMap

class FormsGenerator(spec: TrieMap[String, JsValue]) {
  val openapiTypesToFormTypes: Map[String, String] = Map(
    "integer" -> "number",
    "boolean" -> "bool"
  )

  def run(): Map[String, Form] = {
    // println(spec)
    convertSchemasToForms(JsObject(spec).fields.toSeq)
  }

  def openapiPropertiesToForms(properties: JsObject, typeName: String): JsValue = {
    properties.fields.foldLeft(Json.obj()) { case (props, prop) =>
      val `type`: String   = (prop._2 \ "type").asOpt[String].getOrElse("unknown type")
      val label: String    = prop._1
      var isArray: Boolean = false
      var informations     = Json.obj(
        "label" -> label,
        "type"  -> JsString(openapiTypesToFormTypes.getOrElse(`type`, `type`))
      )

      if (`type` == "object" && (prop._2 \ "properties").asOpt[JsObject].nonEmpty)
        informations = informations ++ Json.obj("type" -> "form", "collapsable" -> true, "collapsed" -> true)
      else if (`type` == "array") {
        isArray = true
        val rawType = (prop._2 \ "items" \ "type").asOpt[String].getOrElse("unknown type")
        val outType = JsString(openapiTypesToFormTypes.getOrElse(rawType, rawType))
        informations = informations ++ Json.obj(
          // "type"   -> "array",
          "array"  -> true,
          "format" -> (if (outType == JsString("object")) JsString("form")
                       else JsNull)
        )
      }

      val `enum` = (prop._2 \ "enum").asOpt[Seq[String]]
      if (`enum`.nonEmpty) {
        informations = Json.obj(
          "type"  -> "select",
          "props" -> Json.obj(
            "label"   -> label,
            "options" -> JsArray(`enum`.getOrElse(Seq.empty).map(JsString.apply))
          )
        )
      }

      if (label == "raw" && `type` == "object") {
        informations = Json.obj(
          "type"  -> "code",
          "props" -> Json.obj(
            "label"      -> label,
            "editorOnly" -> true,
            "mode"       -> "json"
          )
        )
      }

      props ++ Json.obj(
        label -> (if (isArray)
                    (prop._2 \ "items" \ "properties").asOpt[JsObject] match {
                      case Some(subProperties) =>
                        val schema = openapiPropertiesToForms(subProperties, typeName)
                        informations ++ Json.obj(
                          "schema" -> schema,
                          "flow"   -> schema
                            .as[JsObject]
                            .keys
                            .toSeq
                            .sortBy(key => {
                              if ((schema \ key \ "type").as[String] == "object")
                                1
                              else
                                -1
                            })
                        )
                      case None                => informations
                    }
                  else if (`type` == "object")
                    (prop._2 \ "properties").asOpt[JsObject] match {
                      case Some(subProperties) =>
                        val schema = openapiPropertiesToForms(subProperties, typeName)
                        informations ++ Json.obj(
                          "schema" -> schema,
                          "flow"   -> schema.as[JsObject].keys
                        )
                      case None                => informations
                    }
                  else if ((informations \ "type").as[String] == "string" && `enum`.isEmpty) {
                    if (label == "body") {
                      Json.obj(
                        "type"  -> "code",
                        "props" -> Json.obj(
                          "label"      -> label,
                          "editorOnly" -> true,
                          "mode"       -> "json"
                        )
                      )
                    } else
                      informations
                  } else
                    informations)
      )
    }
  }

  def convertSchemasToForms(data: Seq[(String, JsValue)]): Map[String, Form] = {
    data
      .map(schema => {
        val outSchema = (schema._2 \ "properties")
          .asOpt[JsObject]
          .map(obj => openapiPropertiesToForms(obj, schema._1))
          .getOrElse(Json.obj())
          .as[JsValue]

        val schemaAsJsObject = outSchema.as[JsObject]

        schema._1 -> Form(
          schema = schemaAsJsObject,
          flow = outSchema
            .as[JsObject]
            .keys
            .toSeq
            .sortWith((a, _) => {
              schemaAsJsObject.fields
                .find(_._1 == a)
                .map(item => (item._2 \ "type").asOpt[String])
                .forall(item => item.forall(i => !(i == "form")))
            })
        )
      })
      .toMap
  }

}
