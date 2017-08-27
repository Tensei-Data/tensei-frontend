# =============================================================================
# Global methods for the dashboard
# =============================================================================

root = exports ? this

# Start the transformation configuration with the given id.
root.startTransformation = (id) ->
  route = jsRoutes.controllers.DashboardController.enqueue(id)
  $.ajax route.url,
    type: route.method
    dataType: "json"
    error: (jqXHR, textStatus, errorThrown) ->
      console.error("Could not enqueue transformation configuration!")
      console.error(textStatus)
      console.error(errorThrown)
      $('#status-transformation-'+id).html = errorThrown
    success: (data, textStatus, jqXHR) ->
      $('#status-transformation-'+id).html(data)

# Stop the work queue entry with the given uuid.
root.stopTransformation = (id, uuid) ->
  route = jsRoutes.controllers.DashboardController.stopWorkingQueueEntry(uuid)
  $.ajax route.url,
    type: route.method
    dataType: "json"
    error: (jqXHR, textStatus, errorThrown) ->
      console.error("Could not stop transformation configuration!")
      console.error(textStatus)
      console.error(errorThrown)
      $('#status-transformation-'+id).html = errorThrown
    success: (data, textStatus, jqXHR) ->
      $('#status-transformation-'+id).html(data)

window.DashboardWebsocket or= {}

# =============================================================================
# Backend answers
# =============================================================================

# Messages from the server to the client
DashboardWebsocket.outputMsg = (message) ->
  if (message)
    obj = JSON.parse(message)
    if (obj)
      switch
        when obj.type == "dashboard" then DashboardWebsocket.updateDashboardInformation(obj.message)
        when obj.type == "queueInfo" then DashboardWebsocket.updateQueueInformation(obj.message)
        else console.log("Unhandled message from socket: " + message)

# Update the TK entries and the agent information
DashboardWebsocket.updateDashboardInformation = (json) ->
  # TKEntries
  if(json["tkentries"] != undefined)
    DashboardWebsocket.updateTKEntries(json["tkentries"])

  # Agents
  if(json["agents"] != undefined)
    DashboardWebsocket.updateAgentEntries(json["agents"])

# Update the entries of the TKs
DashboardWebsocket.updateTKEntries = (entries) ->
  keys = Object.keys(entries)
  for key in keys
    document.getElementById("status-progress-" + key).innerHTML = entries[key]

# Update the agent information
DashboardWebsocket.updateAgentEntries = (entries) ->
  if(Object.keys(entries) && Object.keys(entries).length > 0)
    keys = Object.keys(entries)
    nodes = document.createElement("span")
    for key in keys
      node = document.createElement("div")
      node.innerHTML = entries[key]
      nodes.appendChild(node)

    document.getElementById("agentContainer").innerHTML = nodes.innerHTML

# Update the information for the queue entries
DashboardWebsocket.updateQueueInformation = (rawInfo) ->
  try
    json = JSON.parse(rawInfo)
    keys = Object.keys(json)
    html = ""
    runner = 0
    for key in keys
      runner = runner + 1
      entry = json[key]
      html += '<span class="entry">' + entry["tkid"] + '</span>'
      if(runner < json.length)
        html += '<span class="glyphicon glyphicon-arrow-right separator"></span>'

    document.getElementById("tcQueueCounter").innerHTML = json.length
    document.getElementById("tcQueueContainer").innerHTML = html
  catch e
    console.log("An error occurred while handling dashboard info: " + json)
    console.error(e)

# =============================================================================
# WebSocket itself
# =============================================================================

DashboardWebsocket.connect = ->
  # SSL -> wss://
  if (window.location.protocol == "https:")
    requestUrl = "wss://"
  else
    requestUrl = "ws://"
  requestUrl = requestUrl + window.location.hostname + ":" + window.location.port + window.location.pathname + "/socket"
  webSocket = new WebSocket(requestUrl)

  webSocket.onopen = (evt) ->
    console.log("Socket open...")

  webSocket.onclose = (evt) ->
    console.log("Socket closed...")

  webSocket.onmessage = (evt) ->
    if (evt && evt.data)
      DashboardWebsocket.outputMsg(evt.data)

  webSocket.onerror = (evt) ->
    console.error("A socket error occurred!")
    DashboardWebsocket.outputMsg(evt.data)

  DashboardWebsocket.sendMessage = (msg) ->
    webSocket.send(msg)

disconnect = ->
  console.log("Socket disconnect...")
  webSocket.disconnect()