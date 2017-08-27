root = exports ? this

$(document).ready ->
  setInterval(root.askServer, 4000)

root.askServer = () ->
  route = jsRoutes.controllers.ApplicationController.checkServerConnection()
  homepage = jsRoutes.controllers.ApplicationController.index()
  $.ajax route.url,
    type: route.method
    contentType: "application/json"
    dataType: "json"
    error: (jqXHR, textStatus, errorThrown) ->
      console.error("An error occured while checking for the server connection!")
      console.error(textStatus)
      console.error(errorThrown)
    success: (data, textStatus, jqXHR) ->
      if(data == true)
        window.location.replace(homepage.url)
      else
        console.log("Server connection not available.")
