root = exports ? this

# Buffer the UUID
currentUuid = null

# Buffer for the last offset.
lastOffset = 0

# A counter for missed updates.
missedUpdate = 0

# Buffer for the interval
intervalBuffer = null

# Cancel the update timer if we missed the update enough times.
cancelUpdate = (force = false) ->
  if (force || missedUpdate > 2)
    console.log("Removing update trigger.")
    clearInterval(intervalBuffer)
    jQuery("#logs-loading").hide()
  else
    missedUpdate = missedUpdate + 1

# Query the websocket for updates.
checkForUpdates = ->
  console.log("Checking for new log entries.")
  currentOffset = getLastOffset()
  if (lastOffset > 0 && lastOffset == currentOffset)
    cancelUpdate()
  lastOffset = currentOffset

  msg =
    "offset": lastOffset
    "uuid": currentUuid

  webSocket.send(JSON.stringify(msg))

# Return the html element of the last log entry.
getLastEntry = ->
  jQuery("#logdata .logline").last()

# Return the offset of the last log entry on the current page.
getLastOffset = ->
  myOffset = if (getLastEntry().length > 0)
    getLastEntry().data("offset")
  else
    0
  myOffset

# Return the uuid from the logdata data attribute.
getUuid = ->
  jQuery("#logdata").data("uuid")

#
# Create a WebSocket connection to the server that is permanently open
# on the respective site.
#
webSocket = null

connect = ->
# SSL -> wss://
  if (window.location.protocol == "https:")
    requestUrl = "wss://"
  else
    requestUrl = "ws://"
  requestUrl = requestUrl + window.location.hostname + ":" + window.location.port + "/logs/socket"
  webSocket = new WebSocket(requestUrl)

  webSocket.onopen = (evt) ->
    console.log("Agent run logs socket open...")
    currentUuid = getUuid()
    intervalBuffer = setInterval(checkForUpdates, 5000)

  webSocket.onclose = (evt) ->
    console.log("Agent run logs socket closed...")
    cancelUpdate(true)

  webSocket.onmessage = (evt) ->
    if (evt && evt.data)
      json = JSON.parse(evt.data)
      if (json.html && json.html.length > 0)
        if (missedUpdate > 0)
          missedUpdate = 0 # Reset the counter for missed updates.
        if (getLastEntry().length > 0)
          getLastEntry().after(json.html)
        else
          jQuery("#logs-loading").before(json.html)

  webSocket.onerror = (evt) ->
    console.error("Agent run logs socket: An error occurred!")
    console.error(evt.data)
    cancelUpdate(true)

disconnect = ->
  console.log("Agent run logs socket disconnect...")
  webSocket.disconnect()
  cancelUpdate(true)

$ ->
  connect()
