package milkman.persistence;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.mapper.JacksonMapper;
import org.dizitart.no2.objects.ObjectRepository;
import org.dizitart.no2.objects.filters.ObjectFilters;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.ReferenceType;

import lombok.val;
import lombok.extern.slf4j.Slf4j;
import milkman.domain.RequestAspect.UnknownRequestAspect;
import milkman.domain.RequestAspect;
import milkman.domain.Workspace;

@Singleton
@Slf4j
public class PersistenceManager {
	
	private Nitrite db;
	private ObjectRepository<Workspace> workspaces;
	private ObjectRepository<OptionEntry> options;
	private ObjectRepository<WorkbenchState> workbenchStates;

	
	public WorkbenchState loadWorkbenchState() {
		WorkbenchState wb = workbenchStates.find().firstOrDefault();
		if (wb == null) {
			wb = new WorkbenchState();
			workbenchStates.insert(wb);
		}
		return wb;
	}
	public void saveWorkbenchState(WorkbenchState state) {
		workbenchStates.update(state);
	}
	
	public List<String> loadWorkspaceNames(){
		List<String> result = new LinkedList<String>();
		for(val workspace : workspaces.find())
			result.add(workspace.getName());
		return result;
	}

	public Optional<Workspace> loadWorkspaceByName(String name) {
		return Optional.ofNullable(workspaces
				.find(ObjectFilters.eq("name", name))
				.firstOrDefault())
				.map(this::fixSerialization);
	}

	private Workspace fixSerialization(Workspace ws) {
		//due to json serialization, the active request is duplicated, but should be references from openRequests
		ws.getOpenRequests().stream()
		.filter(r -> r.getId().equals(ws.getActiveRequest().getId()))
		.findAny()
		.ifPresent(or -> ws.setActiveRequest(or));
		
		return ws;
	}
	
	public void persistWorkspace(Workspace workspace) {
		if (workspace.getId() == 0) {
			long newId = new Random().nextLong(); //todo awkward method to generate new id
			workspace.setId(newId);			
			workspaces.insert(workspace);
		}
		else
			workspaces.update(workspace);
	}
	
	public List<OptionEntry> loadOptions(){
		List<OptionEntry> result = new LinkedList<OptionEntry>();
		for(val option : options.find())
			result.add(option);
		return result;
	}
	
	public void storeOptions(List<OptionEntry> optEntries) {
		options.remove(ObjectFilters.ALL);
		//adjust IDs:
		for(int i = 0; i < optEntries.size(); ++i)
			optEntries.get(i).setId(i);
		options.insert(optEntries.toArray(new OptionEntry[] {}));
	}
	
	
	@PostConstruct
	public void init() {
		JacksonMapper nitriteMapper = new JacksonMapper();
		ObjectMapper mapper = nitriteMapper.getObjectMapper();
		mapper.addHandler(new DeserializationProblemHandler() {
			@Override
			public JavaType handleUnknownTypeId(DeserializationContext ctxt, JavaType baseType, String subTypeId,
					TypeIdResolver idResolver, String failureMsg) throws IOException {
				if (baseType.hasRawClass(RequestAspect.class)) {
					log.error("Unknown AspectType found: " + subTypeId + ". Purging...");
					return ReferenceType.construct(UnknownRequestAspect.class);
				}
				return null;
			}
		});
		
		db = Nitrite.builder()
		        .compressed()
		        .filePath("database.db")
		        .nitriteMapper(nitriteMapper)
		        .openOrCreate("milkman", "bringthemilk");

		workspaces = db.getRepository(Workspace.class);
		options = db.getRepository(OptionEntry.class);
		workbenchStates = db.getRepository(WorkbenchState.class);
	}

	public boolean deleteWorkspace(String workspaceName) {
		return workspaces.remove(ObjectFilters.eq("name", workspaceName)).getAffectedCount() > 0;
	}
	
	
}
