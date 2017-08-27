root = exports ? this

root.drawPieChart = (data, container, height, width) ->
  document.getElementById(container).innerHTML = ""
  color = d3.scale.category20()
  radius = Math.min(width, height) / 2
  vis = d3.select("#"+container)
    .append("svg")
    .data([data])
      .attr("width", width)
      .attr("height", height)
    .append("g")                                                  #make a group to hold our pie chart
      .attr("transform", "translate(" + radius + "," + radius + ")")  #move the center of the pie chart from 0, 0 to radius, radius

  arc = d3.svg.arc()                                                  #this will create <path> elements for us using arc data
    .outerRadius(radius - 10).innerRadius(0)

  pie = d3.layout.pie()                                               #this will create arc data for us given a list of values
    .sort(null)
    .value((d) -> d.value )                                           #we must tell it out to access the value of each element in our data array

  arcs = vis.selectAll(".arc")                                     #this selects all <g> elements with class slice (there aren't any yet)
    .data(pie)                                                        #associate the generated pie data (an array of arcs, each having startAngle, endAngle and value properties)
    .enter()                                                          #this will create <g> elements for every "extra" data element that should be associated with a selection. The result is creating a <g> for every object in the data array
      .append("g")                                                #create a group to hold each slice (we will have a <path> and a <text> element associated with each slice)
        .attr("class", "arc")                                       #allow us to style things in the slices (like text)

  arcsText = vis.selectAll(".arcText")                                     #this selects all <g> elements with class slice (there aren't any yet)
    .data(pie)                                                        #associate the generated pie data (an array of arcs, each having startAngle, endAngle and value properties)
    .enter()                                                          #this will create <g> elements for every "extra" data element that should be associated with a selection. The result is creating a <g> for every object in the data array
      .append("g")                                                #create a group to hold each slice (we will have a <path> and a <text> element associated with each slice)
        .attr("class", "arcText")

  arcs.append("path")
    .attr("d", arc)                                                   #this creates the actual SVG path using the associated data (pie) with the arc drawing function
    .attr("fill", (d, i) -> color(i))                                 #set the color for each slice to be chosen from the color function defined above

  arcsText.append("text")                                             #add a label to each slice
    .attr("transform", (d) -> "translate(" + arc.centroid(d) + ")")
    .attr("dy", ".35em")
    .attr("class", "chartText")
    .text((d, i) -> data[i].label + "(" +data[i].value + ")")

#
# Reusable pie chart via nvd3
#
#
root.nvPieChart = (element, data, width, height) ->
  document.getElementById(element).innerHTML = ""
  d3.selectAll("#"+element)
    .append("svg")
      .attr('style', 'width: ' +width+"px; height: " + height + "px; margin: 0 auto;")

  chart = nv.models.pieChart()
    .width(width)
    .height(height)
    .x((d) -> d.label)
    .y((d) -> d.value)
    .showLabels(true)
    .labelThreshold(.05)
    .labelType("value")           #Can be "key", "value" or "percent"
    .donut(true)
    .valueFormat(d3.format(',.0f'))
    .donutRatio(0.35)

  d3.select("#"+element+" svg")
    .datum(data)
    .transition().duration(0)
    .call(chart)