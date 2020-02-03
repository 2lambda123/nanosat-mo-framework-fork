/* ----------------------------------------------------------------------------
 * Copyright (C) 2015      European Space Agency
 *                         European Space Operations Centre
 *                         Darmstadt
 *                         Germany
 * ----------------------------------------------------------------------------
 * System                : ESA NanoSat MO Framework
 * ----------------------------------------------------------------------------
 * Licensed under the European Space Agency Public License, Version 2.0
 * You may not use this file except in compliance with the License.
 *
 * Except as expressly set forth in this License, the Software is provided to
 * You on an "as is" basis and without warranties of any kind, including without
 * limitation merchantability, fitness for a particular purpose, absence of
 * defects or errors, accuracy or non-infringement of intellectual property rights.
 * 
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 * ----------------------------------------------------------------------------
 */
package esa.mo.nmf.apps;

import esa.mo.nmf.MCRegistration;
import esa.mo.nmf.MCRegistration.RegistrationMode;
import esa.mo.nmf.MonitorAndControlNMFAdapter;
import esa.mo.nmf.NMFInterface;
import esa.mo.nmf.sdk.OrekitResources;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ccsds.moims.mo.mal.provider.MALInteraction;
import org.ccsds.moims.mo.mal.structures.Attribute;
import org.ccsds.moims.mo.mal.structures.Identifier;
import org.ccsds.moims.mo.mal.structures.IdentifierList;
import org.ccsds.moims.mo.mal.structures.UInteger;
import org.ccsds.moims.mo.mc.parameter.structures.ParameterDefinitionDetailsList;
import org.ccsds.moims.mo.mc.parameter.structures.ParameterRawValueList;
import org.ccsds.moims.mo.mc.structures.AttributeValueList;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataProvidersManager;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FactoryManagedFrame;
import org.orekit.frames.FramesFactory;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.ElevationMask;

/**
 * Class for Interfacing with the Camera Acquisitor System. This class handles all Parameters and
 * forwards commands to the corresponding Classes that handle them.
 */
public class CameraAcquisitorSystemMCAdapter extends MonitorAndControlNMFAdapter
{

  private static final Logger LOGGER = Logger.getLogger(
      CameraAcquisitorSystemMCAdapter.class.getName());

  private NMFInterface connector;

  //create earth reference frame:
  public final FactoryManagedFrame earthFrame;
  public final OneAxisEllipsoid earth;
  ;

  private final CameraAcquisitorSystemCameraTargetHandler cameraTargetHandler;
  private final CameraAcquisitorSystemCameraHandler cameraHandler;

  private final CameraAcquisitorSystemGPSHandler gpsHandler;

  private long worstCaseRotationTimeMS = 1000000; //TODO add parameter
  private long attitudeSaftyMarginMS = 20000;//TODO add parameter
  private int maxRetrys = 1;//TODO add parameter

  public int getMaxRetrys()
  {
    return maxRetrys;
  }

  public static final String OREKIT_DATA_PATH = "../";

  public long getWorstCaseRotationTimeMS()
  {
    return worstCaseRotationTimeMS;
  }

  public long getWorstCaseRotationTimeSeconds()
  {
    return worstCaseRotationTimeMS / 1000;
  }

  public CameraAcquisitorSystemCameraHandler getCameraHandler()
  {
    return cameraHandler;
  }

  public CameraAcquisitorSystemCameraTargetHandler getCameraTargetHandler()
  {
    return cameraTargetHandler;
  }

  public CameraAcquisitorSystemGPSHandler getGpsHandler()
  {
    return gpsHandler;
  }

  public NMFInterface getConnector()
  {
    return connector;
  }

  public CameraAcquisitorSystemMCAdapter(final NMFInterface connector)
  {
    FactoryManagedFrame earthFrameTMP = null;
    OneAxisEllipsoid earthTMP = null;
    try {
      //load orekit-data wich is required for many parts of orekit to work.
      LOGGER.log(Level.INFO, "Loading orekit data");
      DataProvidersManager manager = DataProvidersManager.getInstance();
      manager.addProvider(OrekitResources.getOrekitData());

      earthFrameTMP = FramesFactory.getEME2000();
      earthTMP = new OneAxisEllipsoid(
          Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
          Constants.WGS84_EARTH_FLATTENING, earthFrameTMP);

    } catch (OrekitException e) {
      LOGGER.log(Level.SEVERE, "Failed to initialise Orekit:\n{0}", e.getMessage());
    }
    this.earthFrame = earthFrameTMP;
    this.earth = earthTMP;

    this.connector = connector;
    LOGGER.log(Level.INFO, "init cameraTargetHandler");
    this.cameraTargetHandler = new CameraAcquisitorSystemCameraTargetHandler(this);
    LOGGER.log(Level.INFO, "init gpsHandler");
    this.gpsHandler = new CameraAcquisitorSystemGPSHandler(this);
    LOGGER.log(Level.INFO, "init cameraHandler");
    this.cameraHandler = new CameraAcquisitorSystemCameraHandler(this);

  }

  @Override
  public void initialRegistrations(MCRegistration registration)
  {
    LOGGER.log(Level.INFO, "initial registratin");
    // Prevent definition updates on consecutive application runs
    registration.setMode(RegistrationMode.DONT_UPDATE_IF_EXISTS);
    LOGGER.log(Level.INFO, "register parameters");
    registerParameters(registration);
    LOGGER.log(Level.INFO, "register target actions");
    CameraAcquisitorSystemCameraTargetHandler.registerActions(registration);
    LOGGER.log(Level.INFO, "register camera actions");
    CameraAcquisitorSystemCameraHandler.registerActions(registration);
  }

  @Override
  public Attribute onGetValue(Identifier identifier, Byte rawType)
  {
    return null;
  }

  @Override
  public Boolean onSetValue(IdentifierList identifiers, ParameterRawValueList values)
  {
    return false;
  }

  @Override
  public UInteger actionArrived(Identifier name, AttributeValueList attributeValues,
      Long actionInstanceObjId, boolean reportProgress, MALInteraction interaction)
  {

    if (name.getValue() == null) {
      return new UInteger(0);
    }

    LOGGER.log(Level.INFO, "number of parameters: {0}", attributeValues.size());

    switch (name.getValue()) {
      case (CameraAcquisitorSystemCameraTargetHandler.ACTION_PHOTOGRAPH_LOCATION):
        return this.cameraTargetHandler.photographLocation(attributeValues, actionInstanceObjId,
            reportProgress, interaction);
      case (CameraAcquisitorSystemCameraHandler.ACTION_PHOTOGRAPH_NOW):
        return this.cameraHandler.photographNow(attributeValues, actionInstanceObjId,
            reportProgress, interaction);
    }
    return null;
  }

  private void registerParameters(MCRegistration registration)
  {
    ParameterDefinitionDetailsList defs = new ParameterDefinitionDetailsList();
    IdentifierList paramNames = new IdentifierList();
    // TODO add parameters
    // exposure
    // gain
    // resolution
    // ...
    registration.registerParameters(paramNames, defs);
  }

  /**
   * creates an AbsoluteDate object, which contains the current time in UTC
   *
   * @return AbsoluteDate with current time in UTC
   */
  public static AbsoluteDate getNow()
  {
    Instant instant = Instant.now();
    TimeScale utc = TimeScalesFactory.getUTC();
    LocalDateTime time = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"));

    return new AbsoluteDate(time.getYear(), time.getMonthValue(), time.getDayOfMonth(),
        time.getHour(), time.getMinute(), time.getSecond(), utc);
  }

  double getAttitudeSaftyMarginSeconds()
  {
    return this.attitudeSaftyMarginMS / 1000;
  }
}