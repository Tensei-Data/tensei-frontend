$ ->
  $("*[data-overlay]").each ->
    message = $(this).attr('data-overlay')

    $(this).click(->
      body = document.body
      if (body)
        # Create the overlay and set the relevant style information
        overlay = document.createElement("div")
        overlay.id = "overlayWait"
        overlay.style.width = "100%"
        overlay.style.height = "100%"
        # Place the overlay to the page
        body.appendChild(overlay)

        # Info window with the ajax loader image
        infoWidth = 300
        infoBox = document.createElement("div")
        infoBox.id = "overlayWaitInfo"
        infoBox.style.left = (window.innerWidth/2 - infoWidth/2) + "px"
        # the scroll height is also important to add
        infoBox.style.top = (window.innerHeight/2 - 120 + window.scrollY) + "px"
        text = document.createElement("div")
        text.textContent = message
        image = document.createElement("img")
        image.src = "/assets/img/ajax-loader.gif"
        infoBox.appendChild(text)
        infoBox.appendChild(image)
        body.appendChild(infoBox)
    )