$ ->
  window.TransformationConfigurationFormHelpers = {
    cookbookChanged: (cid) ->
      if (typeof cid != "undefined" && cid.length > 0)
        cookbookRoute = jsRoutes.controllers.TransformationConfigurationsController.generateFormData(cid)
        $.ajax cookbookRoute.url,
          method: cookbookRoute.method
          contentType: "application/json"
          dataType: "json"
          error: (jqXHR, textStatus, errorThrown) ->
            console.error("An error occured while trying to load the form data!")
            console.error(textStatus)
            console.error(errorThrown)
          success: (data, textStatus, jqXHR) ->
            $("#sourceConnectionMappings").html(data.sourceMappings)
            $("#targetConnectionMapping").html(data.targetMapping)
      else
        $("#sourceConnectionMappings").html("")
        $("#targetConnectionMapping").html("")
  }

  $("select#cookbookResource").on("change", ->
    TransformationConfigurationFormHelpers.cookbookChanged(this.value)
  )