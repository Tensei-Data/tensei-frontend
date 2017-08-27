root = exports ? this

$(document).ready ->
  setInterval(root.checkLicense, 8000)

root.checkLicense = () ->
  if ($("#disable-license-check").length > 0)
    # License check disabled...
  else
    route = jsRoutes.controllers.ApplicationController.checkLicensePresence()
    $.ajax route.url,
      type: route.method
      contentType: "application/json"
      dataType: "json"
      error: (jqXHR, textStatus, errorThrown) ->
        console.error("An error occured while checking for the presence of a license!")
        console.error(textStatus)
        console.error(errorThrown)
      success: (data, textStatus, jqXHR) ->
        if(data == true)
          document.getElementById("licenseMissingBox").setAttribute("style", "display:none;")
        else
          document.getElementById("licenseMissingBox").setAttribute("style", "display:block;")
          console.log("No license installed.")
