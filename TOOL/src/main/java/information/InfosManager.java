package information;

import org.activiti.engine.impl.util.json.JSONObject;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Scanner;

public class InfosManager
{

	private final ArrayList<Information> informationList;
	private final File workingDirectory;

	public InfosManager(final File workingDirectory)
	{
		this.workingDirectory = workingDirectory;
		this.informationList = new ArrayList<>();
	}

	public void loadData() throws IOException
	{
		final File informationFile = new File(Path.of(this.workingDirectory.getAbsolutePath(), InfoConstants.INFO_FILE_NAME).toString());

		if (!informationFile.exists())
		{
			//The file does not exist yet => first step
		}
		else
		{
			//The file exists, read it and load its content
			final FileInputStream inputStream = new FileInputStream(informationFile);
			final Scanner scanner = new Scanner(inputStream);

			while (scanner.hasNextLine())
			{
				final JSONObject jsonObject = new JSONObject(scanner.nextLine());
				this.informationList.add(Information.load(jsonObject));
			}

			scanner.close();
			inputStream.close();
		}
	}

	public void writeData() throws FileNotFoundException
	{
		final File informationFile = new File(Path.of(this.workingDirectory.getAbsolutePath(), InfoConstants.INFO_FILE_NAME).toString());
		final PrintWriter printWriter = new PrintWriter(informationFile);

		for (Information information : this.informationList)
		{
			printWriter.println(information.jsonize().toString());
		}

		printWriter.flush();
		printWriter.close();
	}

	public void addInformation(final Information information)
	{
		this.informationList.add(information);
	}

	public boolean isEmpty()
	{
		return this.informationList.isEmpty();
	}
}
