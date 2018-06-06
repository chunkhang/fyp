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
      eventAfterAllRender: handleDoneRenderAllEvents
    });
  }

  function handleDoneRenderEvent(event, element) {
    // Set tooltip text
    $(element).attr("title", event.venue);
  }

  function handleDoneRenderAllEvents() {
    // Initalize tooltips
    $(".fc-event").tooltip({
      placement: "right"
    });
  }

}
