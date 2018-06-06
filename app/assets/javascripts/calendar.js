/* globals $ */

export function calendar() {

  // Initialize calendar
  var calendar = $("#calendar");
  initializeCalender();

  function initializeCalender() {
    calendar.fullCalendar({
      schedulerLicenseKey: "CC-Attribution-NonCommercial-NoDerivatives",
      eventSources: [
        {
          url: "/calendar/events",
          type: "GET",
          error: function() {
            console.log("Failed to fetch calendar events");
          }
        }
      ],
      header: {
        left: "month,agendaWeek,listWeek",
        center: "title"
      },
      buttonText: {
        month: "Month",
        agendaWeek: "Week",
        listWeek: "List",
        today: "Today"
      },
      nowIndicator: true,
      businessHours: {
        dow: [1, 2, 3, 4, 5, 6],
        start: "08:00",
        end: "18:00"
      },
      minTime: "08:00:00",
      maxTime: "18:00:00",
      contentHeight: "auto",
      eventAfterRender: handleDoneRenderEvent,
      eventAfterAllRender: handleDoneRenderAllEvents,
      dayClick: handleClickDay
    });
  }

  function handleDoneRenderEvent(event, element) {
    // Set popover content
    $(element).attr("title", event.description);
    $(element).attr("data-content", "Hello World!");
    $(element).attr("data-trigger", "focus");
    $(element).attr("tabindex", 0);
  }

  function handleDoneRenderAllEvents() {
    // Initalize popovers
    var events = $(".fc-event");
    events.popover({
      placement: "bottom",
      html: true
    });
    events.on("show.bs.popover", function() {
      console.log("Show!");
      popoverShowing = true;
    });
    events.on("hide.bs.popover", function() {
      console.log("Hide!");
      popoverShowing = false;
    });
  }

  var popoverShowing = false;
  function handleClickDay() {
    // Hide popovers
    if (popoverShowing) {
      $(".fc-event").popover("hide");
    }
  }

}
