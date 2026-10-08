package org.ugaddress.register.register;

import java.util.ArrayList;
import java.util.List;
import org.ugaddress.api.v1.model.AddressableObject;
import org.ugaddress.api.v1.model.AdminUnitRef;
import org.ugaddress.api.v1.model.Alias;
import org.ugaddress.api.v1.model.Entrance;
import org.ugaddress.api.v1.model.Lifecycle;
import org.ugaddress.api.v1.model.NationalIdStatus;
import org.ugaddress.api.v1.model.ObjectKind;
import org.ugaddress.api.v1.model.ThoroughfareRef;
import org.ugaddress.nationalid.NationalId;
import org.ugaddress.register.gazetteer.AdminUnitDTO;
import org.ugaddress.register.shared.GeoJson;

/**
 * Maps register DTOs to the API representation of the contract.
 */
public final class ObjectApiMapper {

    private ObjectApiMapper() {
    }

    /**
     * Maps an object.
     *
     * @param object the (already redacted) object
     * @return the API representation
     */
    public static AddressableObject toApi(final AddressableObjectDTO object) {
        final AddressableObject api = new AddressableObject(object.id(), object.nationalId(),
            displayId(object.nationalId(), object.demonstration()),
            object.demonstration() ? NationalIdStatus.DEMONSTRATION : NationalIdStatus.ALLOCATED,
            ObjectKind.fromValue(object.kind()), Lifecycle.fromValue(object.lifecycle()),
            object.entrances().stream().map(ObjectApiMapper::entrance).toList(),
            object.aliases().stream().map(a -> new Alias(a.system(), a.value())).toList(),
            object.version());
        api.setName(object.name());
        api.setLocation(GeoJson.toApi(object.location()));
        api.setValidFrom(object.validFrom());
        if (object.address() != null) {
            api.setAddress(address(object.address()));
        }
        return api;
    }

    /**
     * Returns the display form of a national ID, with the {@code DEMO} marker for demonstration IDs.
     *
     * @param digits the 11 digits
     * @param demonstration whether the ID is a demonstration ID
     * @return the display form
     */
    public static String displayId(final String digits, final boolean demonstration) {
        final NationalId id = NationalId.of(digits);
        return demonstration ? id.formatAsDemonstration() : id.format();
    }

    private static Entrance entrance(final AddressableObjectDTO.Entrance entrance) {
        final Entrance api = new Entrance(entrance.id(), entrance.nationalId(),
            displayId(entrance.nationalId(), entrance.demonstration()), entrance.residential());
        api.setMain(entrance.main());
        api.setLocation(GeoJson.toApi(entrance.location()));
        return api;
    }

    private static org.ugaddress.api.v1.model.Address address(final AddressableObjectDTO.Address address) {
        final List<AdminUnitRef> units = address.adminUnits().stream()
            .map(u -> new AdminUnitRef(u.id(), AdminUnitRef.LevelEnum.fromValue(u.level()), u.name()))
            .toList();
        final org.ugaddress.api.v1.model.Address api = new org.ugaddress.api.v1.model.Address(address.id(),
            address.houseNumber(), new ThoroughfareRef(address.thoroughfareId(), address.thoroughfareName()), units,
            lines(address));
        api.setUnit(address.unit());
        api.setPostcode(address.postcode());
        return api;
    }

    private static List<String> lines(final AddressableObjectDTO.Address address) {
        final List<String> lines = new ArrayList<>();
        final String unit = address.unit() == null ? "" : address.unit() + "/";
        lines.add(unit + address.houseNumber() + " " + address.thoroughfareName());
        for (final AdminUnitDTO unitDto : address.adminUnits()) {
            if (!"region".equals(unitDto.level())) {
                lines.add(unitDto.name());
            }
        }
        if (address.postcode() != null) {
            lines.add(address.postcode());
        }
        return lines;
    }
}
